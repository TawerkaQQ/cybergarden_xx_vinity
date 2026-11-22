package cybergarden.vinity.api

import kotlin.test.Test
import kotlin.test.assertEquals

class AppRouteTest {

    @Test
    fun ragChatRouteRoundTrips() {
        val route = AppRoute(AppScreen.RAG)
        assertEquals("/rag-chat", route.encode())
        assertEquals(route, AppRoute.parse("#/rag-chat"))
    }

    @Test
    fun ragAreaStaysAnArticleSet() {
        assertEquals(AppRoute(AppScreen.ARTICLES, SetRef("rag", "dev")), AppRoute.parse("#/rag/dev"))
        assertEquals(AppRoute(AppScreen.ASK, SetRef("rag", "dev")), AppRoute.parse("#/rag/dev/ask"))
    }

    @Test
    fun ragChatIgnoresSelectedSet() {
        assertEquals("/rag-chat", AppRoute(AppScreen.RAG, SetRef("solar", "dev")).encode())
    }
}
