# Sprinklr Chat UI

A modern React + TypeScript frontend for the Sprinklr Agentic AI Chatbot.

## Features

- ✨ Real-time streaming chat using Server-Sent Events (SSE)
- 📝 Markdown rendering for formatted responses
- 💅 Responsive design with Tailwind CSS
- 🎯 Message history with user/assistant distinction
- ⚡ Built with Vite for fast development and builds
- 🔄 Auto-expanding textarea input
- 🎨 Beautiful UI with lucide-react icons

## Tech Stack

- **React 18** - UI framework
- **TypeScript** - Type safety
- **Vite** - Build tool
- **Tailwind CSS** - Styling
- **@microsoft/fetch-event-source** - SSE POST support
- **react-markdown** - Markdown rendering
- **lucide-react** - Icons

## Getting Started

### Prerequisites

- Node.js 16+ and npm

### Installation

```bash
# Install dependencies
npm install
```

### Development

```bash
# Start development server
npm run dev
```

The app will be available at `http://localhost:5173`

### Building

```bash
# Build for production
npm run build

# Preview production build
npm run preview
```

## API Configuration

The frontend connects to the backend at:
- **Base URL:** `http://localhost:8080`
- **Endpoint:** `/api/v1/chat/stream`
- **Method:** POST
- **Content-Type:** application/json

**Request Body:**
```json
{
  "userId": "user-123",
  "conversationId": "conv-456",
  "prompt": "Your message here"
}
```

The backend should return streaming responses via Server-Sent Events with the format:
```
data: {text chunk}
```

## Project Structure

```
src/
├── components/
│   └── Chat.tsx          # Main chat component
├── services/
│   └── chatService.ts    # API communication logic
├── types/
│   └── chat.ts          # TypeScript type definitions
├── App.tsx              # Root component
├── main.tsx             # Entry point
└── index.css            # Tailwind styles
```

## Key Components

### Chat Component

The main chat interface featuring:
- Scrollable message history
- Real-time text streaming
- Markdown rendering for AI responses
- User/Assistant message distinction with avatars
- Loading states and error handling
- Auto-expanding input textarea

### Chat Service

Handles SSE streaming:
- Uses `@microsoft/fetch-event-source` for POST-based SSE
- Manages connection lifecycle
- Handles errors and streaming events

## Development Notes

- The chat generates random user and conversation IDs on app load
- All styling uses Tailwind CSS utility classes
- The UI is fully responsive from mobile to desktop
- Enter key sends message, Shift+Enter creates new line

## License

MIT
