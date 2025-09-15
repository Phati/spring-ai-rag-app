# Spring AI RAG Application

A comprehensive Retrieval-Augmented Generation (RAG) application built with Spring AI, Ollama, and PostgreSQL vector database.

## Features

- 📄 PDF document ingestion and processing
- 🔍 Semantic search using vector embeddings
- 🤖 LLM-powered question answering
- 📊 PostgreSQL with pgvector for vector storage
- 🚀 RESTful API with comprehensive documentation
- 🔧 Production-ready error handling and logging

## Technology Stack

- **Spring Boot 3.2+** - Application framework
- **Spring AI** - AI integration framework
- **Ollama** - Local LLM runtime
- **PostgreSQL + pgvector** - Vector database
- **Apache PDFBox** - PDF processing
- **OpenAPI 3** - API documentation

## Prerequisites

- Java 21+
- Docker and Docker Compose
- Maven 3.6+

## Quick Start

### 1. Start Infrastructure Services

```bash
cd docker
docker-compose up -d
```

This will start:
- PostgreSQL with pgvector extension
- Ollama with required models (llama3.1:8b, nomic-embed-text)

### 2. Run the Application

```bash
mvn spring-boot:run
```

### 3. Access the Application

- **API Documentation**: http://localhost:8080/swagger-ui.html
- **Health Check**: http://localhost:8080/actuator/health

## API Endpoints

### Document Management

#### Upload PDF Document
```bash
curl -X POST "http://localhost:8080/api/documents/upload" \
     -H "Content-Type: multipart/form-data" \
     -F "file=@document.pdf" \
     -F "chunkSize=1000" \
     -F "chunkOverlap=200"
```

#### Get All Documents
```bash
curl -X GET "http://localhost:8080/api/documents"
```

#### Get Document by ID
```bash
curl -X GET "http://localhost:8080/api/documents/{id}"
```

#### Delete Document
```bash
curl -X DELETE "http://localhost:8080/api/documents/{id}"
```

### RAG Queries

#### Process Query
```bash
curl -X POST "http://localhost:8080/api/query" \
     -H "Content-Type: application/json" \
     -d '{
       "query": "What is the main topic of the documents?",
       "topK": 5,
       "similarityThreshold": 0.7
     }'
```

## Configuration

### Application Properties

Key configuration options in `application.yml`:

```yaml
spring:
  ai:
    ollama:
      base-url: http://localhost:11434
      chat:
        model: llama3.1:8b
      embedding:
        model: nomic-embed-text
    vectorstore:
      pgvector:
        dimensions: 768
```

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `POSTGRES_URL` | Database URL | `jdbc:postgresql://localhost:5432/rag_db` |
| `POSTGRES_USER` | Database user | `postgres` |
| `POSTGRES_PASSWORD` | Database password | `postgres` |
| `OLLAMA_BASE_URL` | Ollama service URL | `http://localhost:11434` |

## Architecture

### High-Level Architecture

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Client App    │    │  Spring Boot    │    │   PostgreSQL    │
│                 │────│   Application   │────│   + pgvector    │
│   (REST API)    │    │                 │    │                 │
└─────────────────┘    └─────────────────┘    └─────────────────┘
                                │
                                │
                       ┌─────────────────┐
                       │     Ollama      │
                       │   (LLM + EMB)   │
                       └─────────────────┘
```

### Component Flow

1. **Document Ingestion**: PDF → Text Extraction → Chunking → Embedding → Vector Store
2. **Query Processing**: Query → Embedding → Similarity Search → Context → LLM → Response

## Development

### Running Tests

```bash
mvn test
```

### Building for Production

```bash
mvn clean package -Pprod
```

### Code Quality

The project includes:
- Comprehensive error handling
- Request/response validation
- Structured logging
- API documentation
- Health checks

## Monitoring

### Health Checks

- Application: `GET /actuator/health`
- Database: Automatic connection health check
- Ollama: Model availability check

### Metrics

Available at `/actuator/metrics`:
- Document processing metrics
- Query performance metrics
- Vector store statistics

## Troubleshooting

### Common Issues

1. **Ollama Models Not Found**
   ```bash
   docker exec -it ollama ollama pull llama3.1:8b
   docker exec -it ollama ollama pull nomic-embed-text
   ```

2. **Database Connection Issues**
    - Verify PostgreSQL is running: `docker ps`
    - Check connection: `docker exec -it postgres psql -U postgres -d rag_db`

3. **Large File Upload Errors**
    - Check `spring.servlet.multipart.max-file-size` setting
    - Verify available disk space

### Logs

Application logs include:
- Document processing status
- Query performance metrics
- Error details with stack traces

## Contributing

1. Fork the repository
2. Create a feature branch
3. Add tests for new functionality
4. Submit a pull request

## License

This project is licensed under the MIT License.