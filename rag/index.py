from ingestion import ingest, load_pdfs_from_data_dir

def main():
    documents = load_pdfs_from_data_dir()
    ingest(documents)

if __name__ == "__main__":
    main()