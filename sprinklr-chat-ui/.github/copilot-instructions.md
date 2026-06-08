# Sprinklr Chat UI - Project Setup Instructions

## Project Overview
A modern React + TypeScript frontend for the Sprinklr Agentic AI Chatbot using Vite, Tailwind CSS, and streaming SSE support via POST requests.

## Setup Checklist

- [x] Scaffold the Vite Project
- [x] Install Project Dependencies
- [x] Configure Tailwind CSS
- [x] Create Chat Components
- [x] Set up Environment Configuration
- [x] Build and Verify Project
- [x] Create Development Task
- [ ] Launch Development Server

## Development Notes

**Backend API Endpoint:** `http://localhost:8080/api/v1/chat/stream`

**Key Technologies:**
- Vite + React + TypeScript
- Tailwind CSS for styling
- react-markdown for markdown rendering
- @microsoft/fetch-event-source for SSE POST support
- lucide-react for icons

**Running the Project:**
```bash
npm install
npm run dev
```

The frontend will run on http://localhost:5173 by default.
