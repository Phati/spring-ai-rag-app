// Spring AI RAG App JavaScript

class RAGApp {
    constructor() {
        this.initializeEventListeners();
        this.setupFormSubmissions();
    }

    initializeEventListeners() {
        // Upload form submission
        document.getElementById('uploadForm').addEventListener('submit', (e) => {
            e.preventDefault();
            this.handleFileUpload();
        });

        // Chat form submission
        document.getElementById('chatForm').addEventListener('submit', (e) => {
            e.preventDefault();
            this.handleChatMessage();
        });

        // Enter key in message input
        document.getElementById('messageInput').addEventListener('keypress', (e) => {
            if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                this.handleChatMessage();
            }
        });
    }

    setupFormSubmissions() {
        // Auto-scroll chat messages
        this.scrollToBottom();
    }

    async handleFileUpload() {
        const form = document.getElementById('uploadForm');
        const formData = new FormData();
        
        // Get form values
        const fileInput = document.getElementById('file');
        const file = fileInput.files[0];
        
        if (!file) {
            this.showAlert('Please select a file to upload.', 'danger');
            return;
        }

        // Validate file size (50MB)
        if (file.size > 50 * 1024 * 1024) {
            this.showAlert('File size cannot exceed 50MB.', 'danger');
            return;
        }

        // Prepare form data
        formData.append('file', file);
        formData.append('description', document.getElementById('description').value);
        formData.append('chunkSize', document.getElementById('chunkSize').value);
        formData.append('chunkOverlap', document.getElementById('chunkOverlap').value);

        // Show progress
        this.showUploadProgress();

        try {
            const response = await fetch('/spring-ai-rag/api/documents/upload', {
                method: 'POST',
                body: formData
            });

            if (response.ok) {
                const result = await response.json();
                this.showUploadSuccess(result);
                this.resetUploadForm();
                this.addSystemMessage(`Document "${result.filename}" uploaded successfully! ${result.totalChunks} chunks created.`);
            } else {
                const errorData = await response.json();
                throw new Error(errorData.message || 'Upload failed');
            }
        } catch (error) {
            console.error('Upload error:', error);
            this.showUploadError(error.message);
        } finally {
            this.hideUploadProgress();
        }
    }

    async handleChatMessage() {
        const messageInput = document.getElementById('messageInput');
        const message = messageInput.value.trim();

        if (!message) {
            return;
        }

        // Add user message to chat
        this.addUserMessage(message);
        messageInput.value = '';

        // Show typing indicator
        const typingId = this.showTypingIndicator();

        try {
            const requestData = {
                query: message,
                topK: parseInt(document.getElementById('topK').value),
                similarityThreshold: parseFloat(document.getElementById('threshold').value),
                includeMetadata: true
            };

            const response = await fetch('/spring-ai-rag/api/query', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(requestData)
            });

            if (response.ok) {
                const result = await response.json();
                this.removeTypingIndicator(typingId);
                this.addAssistantMessage(result.answer, result.sources);
            } else {
                const errorData = await response.json();
                throw new Error(errorData.message || 'Query failed');
            }
        } catch (error) {
            console.error('Chat error:', error);
            this.removeTypingIndicator(typingId);
            this.addAssistantMessage('Sorry, I encountered an error while processing your question. Please try again.');
        }
    }

    addUserMessage(message) {
        const chatMessages = document.getElementById('chatMessages');
        const messageElement = document.createElement('div');
        messageElement.className = 'message user-message';
        messageElement.innerHTML = `
            <div class="message-content">
                ${this.escapeHtml(message)}
            </div>
            <small class="message-time">${this.getCurrentTime()}</small>
        `;
        chatMessages.appendChild(messageElement);
        this.scrollToBottom();
    }

    addAssistantMessage(message, sources = []) {
        const chatMessages = document.getElementById('chatMessages');
        const messageElement = document.createElement('div');
        messageElement.className = 'message assistant-message';
        
        let sourcesHtml = '';
        if (sources && sources.length > 0) {
            sourcesHtml = `
                <div class="source-links">
                    <strong>Sources:</strong><br>
                    ${sources.map(source => `
                        <span class="source-link" title="Chunk ${source.chunkIndex} from ${source.filename}">
                            ${source.filename} (${source.chunkIndex})
                        </span>
                    `).join('')}
                </div>
            `;
        }

        messageElement.innerHTML = `
            <div class="message-content">
                <i class="fas fa-robot me-2"></i>
                ${this.formatMessage(message)}
                ${sourcesHtml}
            </div>
            <small class="message-time">${this.getCurrentTime()}</small>
        `;
        chatMessages.appendChild(messageElement);
        this.scrollToBottom();
    }

    addSystemMessage(message) {
        const chatMessages = document.getElementById('chatMessages');
        const messageElement = document.createElement('div');
        messageElement.className = 'message assistant-message';
        messageElement.innerHTML = `
            <div class="message-content">
                <i class="fas fa-info-circle me-2"></i>
                ${this.escapeHtml(message)}
            </div>
            <small class="message-time">${this.getCurrentTime()}</small>
        `;
        chatMessages.appendChild(messageElement);
        this.scrollToBottom();
    }

    showTypingIndicator() {
        const chatMessages = document.getElementById('chatMessages');
        const typingId = 'typing-' + Date.now();
        const typingElement = document.createElement('div');
        typingElement.className = 'message assistant-message';
        typingElement.id = typingId;
        typingElement.innerHTML = `
            <div class="message-content">
                <i class="fas fa-robot me-2"></i>
                <span class="typing-indicator">
                    <span class="spinner-border spinner-border-sm me-2" role="status"></span>
                    Thinking...
                </span>
            </div>
        `;
        chatMessages.appendChild(typingElement);
        this.scrollToBottom();
        return typingId;
    }

    removeTypingIndicator(typingId) {
        const typingElement = document.getElementById(typingId);
        if (typingElement) {
            typingElement.remove();
        }
    }

    showUploadProgress() {
        document.getElementById('uploadProgress').style.display = 'block';
        document.getElementById('uploadBtn').disabled = true;
        document.getElementById('uploadBtn').innerHTML = `
            <span class="spinner-border spinner-border-sm me-2" role="status"></span>
            Processing...
        `;
    }

    hideUploadProgress() {
        document.getElementById('uploadProgress').style.display = 'none';
        document.getElementById('uploadBtn').disabled = false;
        document.getElementById('uploadBtn').innerHTML = `
            <i class="fas fa-upload me-2"></i>Upload & Process
        `;
    }

    showUploadSuccess(result) {
        const resultDiv = document.getElementById('uploadResult');
        resultDiv.innerHTML = `
            <div class="alert alert-success">
                <i class="fas fa-check-circle me-2"></i>
                Success! ${result.totalChunks} chunks created from "${result.filename}"
            </div>
        `;
        resultDiv.style.display = 'block';
        
        setTimeout(() => {
            resultDiv.style.display = 'none';
        }, 5000);
    }

    showUploadError(error) {
        const resultDiv = document.getElementById('uploadResult');
        resultDiv.innerHTML = `
            <div class="alert alert-danger">
                <i class="fas fa-exclamation-circle me-2"></i>
                Error: ${this.escapeHtml(error)}
            </div>
        `;
        resultDiv.style.display = 'block';
        
        setTimeout(() => {
            resultDiv.style.display = 'none';
        }, 8000);
    }

    showAlert(message, type) {
        const resultDiv = document.getElementById('uploadResult');
        resultDiv.innerHTML = `
            <div class="alert alert-${type}">
                <i class="fas fa-exclamation-circle me-2"></i>
                ${this.escapeHtml(message)}
            </div>
        `;
        resultDiv.style.display = 'block';
        
        setTimeout(() => {
            resultDiv.style.display = 'none';
        }, 5000);
    }

    resetUploadForm() {
        document.getElementById('uploadForm').reset();
        document.getElementById('chunkSize').value = '800';
        document.getElementById('chunkOverlap').value = '100';
    }

    scrollToBottom() {
        const chatMessages = document.getElementById('chatMessages');
        setTimeout(() => {
            chatMessages.scrollTop = chatMessages.scrollHeight;
        }, 100);
    }

    getCurrentTime() {
        return new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    }

    escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    formatMessage(message) {
        // Basic markdown-like formatting
        return message
            .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
            .replace(/\*(.*?)\*/g, '<em>$1</em>')
            .replace(/\n/g, '<br>')
            .replace(/`(.*?)`/g, '<code>$1</code>');
    }
}

// Initialize the app when DOM is loaded
document.addEventListener('DOMContentLoaded', () => {
    new RAGApp();
});