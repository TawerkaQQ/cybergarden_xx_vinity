import ipaddress
import json
import re
import socket
import ssl
from collections import Counter
from concurrent.futures import ThreadPoolExecutor
from contextlib import closing
from dataclasses import dataclass
from datetime import UTC, datetime
from html.parser import HTMLParser
from http.client import HTTPConnection, HTTPException
from urllib.parse import parse_qs, urlencode, urljoin, urlsplit, urlunsplit
from urllib.request import Request, urlopen

from config import get_searxng_url
from evidence import WebPassage
from ingestion import split_text

DUCKDUCKGO_URL = "https://lite.duckduckgo.com/lite/"
USER_AGENT = "Mozilla/5.0 (compatible; VinityRAG/1.0)"
MAX_SEARCH_BYTES = 512_000
MAX_PAGE_BYTES = 1_000_000
MAX_PAGES = 6
MAX_PASSAGES = 6
REQUEST_TIMEOUT = 7
REDIRECT_CODES = {301, 302, 303, 307, 308}
IGNORE_TAGS = {"script", "style", "noscript", "head", "nav", "footer", "aside", "svg", "form"}
BLOCK_TAGS = {"address", "article", "blockquote", "br", "dd", "div", "dt", "h1", "h2", "h3", "li", "p", "pre", "section", "td"}
WORD = re.compile(r"[^\W_]{3,}")
SSL_CONTEXT = ssl.create_default_context()


@dataclass(frozen=True)
class SearchHit:
    title: str
    url: str


class SearchResultsParser(HTMLParser):
    def __init__(self):
        super().__init__()
        self.results = []
        self.href = None
        self.title = []

    def handle_starttag(self, tag, attrs):
        attributes = dict(attrs)
        if tag == "a" and "result-link" in (attributes.get("class") or "").split():
            self.href = attributes.get("href")
            self.title = []

    def handle_data(self, data):
        if self.href:
            self.title.append(data)

    def handle_endtag(self, tag):
        if tag == "a" and self.href:
            url = urljoin(DUCKDUCKGO_URL, self.href)
            parsed = urlsplit(url)
            if parsed.hostname in {"duckduckgo.com", "lite.duckduckgo.com"} and parsed.path == "/l/":
                url = parse_qs(parsed.query).get("uddg", [""])[0]
            title = " ".join("".join(self.title).split())
            if title and url:
                self.results.append(SearchHit(title, url))
            self.href = None


class PageTextParser(HTMLParser):
    def __init__(self):
        super().__init__()
        self.ignored = 0
        self.current = []
        self.blocks = []

    def handle_starttag(self, tag, attrs):
        if tag in IGNORE_TAGS:
            self.ignored += 1
        if tag in BLOCK_TAGS and not self.ignored:
            self.flush()

    def handle_endtag(self, tag):
        if tag in IGNORE_TAGS and self.ignored:
            self.ignored -= 1
        if tag in BLOCK_TAGS and not self.ignored:
            self.flush()

    def handle_data(self, data):
        if not self.ignored:
            self.current.append(data)

    def flush(self):
        text = " ".join("".join(self.current).split())
        if text:
            self.blocks.append(text)
        self.current.clear()


def search_public_web(query: str) -> list[SearchHit]:
    searxng = get_searxng_url()
    if searxng:
        endpoint = searxng if searxng.endswith("/search") else f"{searxng}/search"
        if urlsplit(endpoint).scheme not in {"http", "https"}:
            raise ValueError("SEARXNG_URL должен быть адресом HTTP или HTTPS")
        url = f"{endpoint}?{urlencode({'q': query, 'format': 'json'})}"
        payload = json.loads(read_search_response(url))
        if not isinstance(payload, dict) or not isinstance(results := payload.get("results"), list):
            raise ValueError("SearXNG вернул ответ в неверном формате")
        return [SearchHit(row["title"], row["url"]) for row in results if isinstance(row, dict)
                and isinstance(row.get("title"), str) and isinstance(row.get("url"), str)]

    parser = SearchResultsParser()
    parser.feed(read_search_response(f"{DUCKDUCKGO_URL}?{urlencode({'q': query})}"))
    parser.close()
    return parser.results


def read_search_response(url: str) -> str:
    request = Request(url, headers={"User-Agent": USER_AGENT})
    try:
        with urlopen(request, timeout=REQUEST_TIMEOUT) as response:
            return response.read(MAX_SEARCH_BYTES).decode("utf-8", errors="replace")
    except (OSError, HTTPException) as error:
        raise OSError("Публичный поисковый сервис недоступен") from error


def open_public_socket(host: str, port: int) -> socket.socket:
    addresses = socket.getaddrinfo(host, port, type=socket.SOCK_STREAM)
    ip = next((address[4][0] for address in addresses if ipaddress.ip_address(address[4][0]).is_global), None)
    if ip is None:
        raise ValueError("Источник не имеет публичного сетевого адреса")
    return socket.create_connection((ip, port), timeout=REQUEST_TIMEOUT)


def read_public_page(url: str) -> tuple[str, str]:
    for _ in range(4):
        parsed = urlsplit(url)
        if parsed.scheme not in {"http", "https"} or not parsed.hostname or parsed.username or parsed.password:
            raise ValueError("Источник должен быть публичным адресом HTTP или HTTPS")
        port = 443 if parsed.scheme == "https" else 80
        if parsed.port not in {None, port}:
            raise ValueError("Источник использует нестандартный порт")
        host = parsed.hostname.encode("idna").decode("ascii")
        path = urlunsplit(("", "", parsed.path or "/", parsed.query, ""))
        with closing(HTTPConnection(host, port, timeout=REQUEST_TIMEOUT)) as connection:
            connection.sock = open_public_socket(host, port)
            if parsed.scheme == "https":
                connection.sock = SSL_CONTEXT.wrap_socket(connection.sock, server_hostname=host)
            connection.request("GET", path, headers={"User-Agent": USER_AGENT, "Accept": "text/html,text/plain"})
            with closing(connection.getresponse()) as response:
                if response.status in REDIRECT_CODES and response.headers.get("Location"):
                    url = urljoin(url, response.headers["Location"])
                    continue
                if response.status != 200:
                    raise OSError(f"Страница вернула HTTP {response.status}")
                media_type = response.headers.get_content_type()
                if media_type not in {"text/html", "text/plain", "application/xhtml+xml"}:
                    raise ValueError("У источника нет поддерживаемого текстового формата")
                charset = response.headers.get_content_charset() or "utf-8"
                body = response.read(MAX_PAGE_BYTES).decode(charset, errors="replace")
                if media_type != "text/plain":
                    parser = PageTextParser()
                    parser.feed(body)
                    parser.close()
                    parser.flush()
                    body = " ".join(parser.blocks)
                return urlunsplit(parsed._replace(fragment="")), " ".join(body.split())
    raise OSError("Слишком много перенаправлений на веб-странице")


def passages_for_hit(hit: SearchHit, terms: set[str]) -> list[tuple[int, WebPassage]]:
    try:
        url, text = read_public_page(hit.url)
    except (OSError, ValueError, LookupError, HTTPException):
        return []
    fetched_at = datetime.now(UTC).isoformat(timespec="seconds")
    passages = []
    for chunk in split_text(text):
        if len(chunk) < 60:
            continue
        words = Counter(WORD.findall(chunk.casefold()))
        score = sum(min(words[term], 3) for term in terms)
        passages.append((score, WebPassage(" ".join(hit.title.split()), url, chunk, fetched_at)))
    return sorted(passages, key=lambda item: item[0], reverse=True)[:2]


def find_web_passages(question: str, phrases: list[str]) -> list[WebPassage]:
    keywords = re.findall(r"[^\W_]+", " ".join(phrases[:4]).casefold())
    compact = " ".join(list(dict.fromkeys(keywords))[:10])
    terms = set(WORD.findall(" ".join([question, *phrases]).casefold()))
    queries = dict.fromkeys(value[:320].strip() for value in [compact, *phrases[:1], question] if value.strip())
    search_hits = []
    for query in queries:
        search_hits = search_public_web(query)
        if search_hits:
            break
    hits = list({hit.url: hit for hit in search_hits}.values())[:MAX_PAGES]
    if not hits:
        return []

    with ThreadPoolExecutor(max_workers=4) as pool:
        ranked = [result for group in pool.map(lambda hit: passages_for_hit(hit, terms), hits) for result in group]
    ranked.sort(key=lambda item: item[0], reverse=True)
    unique = dict.fromkeys(passage for score, passage in ranked if score or not terms)
    return list(unique)[:MAX_PASSAGES]
