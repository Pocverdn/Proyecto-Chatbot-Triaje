"use client";
import { useEffect, useRef, useState } from "react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";

export default function Chat() {
  const [userId, setUserId] = useState("");
  const [input, setInput] = useState("");
  const [messages, setMessages] = useState([]);
  const [isStreaming, setIsStreaming] = useState(false);
  const eventSourceRef = useRef(null);

  // Load or create user ID
  useEffect(() => {
    let savedUser = localStorage.getItem("user");
    if (!savedUser) {
      savedUser = crypto.randomUUID();
      localStorage.setItem("user", savedUser);
    }
    setUserId(savedUser);
  }, []);

  // Load chat history
  useEffect(() => {
    if (!userId) return;
    loadHistory();
  }, [userId]);

  async function loadHistory() {
    try {
      const res = await fetch(`${process.env.NEXT_PUBLIC_API_BASE_URL}/chat/${userId}`);
      if (!res.ok) throw new Error("Failed to fetch chat history");
      const data = await res.json();
      setMessages(data.reverse());
    } catch (err) {
      console.error("Error loading chat:", err);
    }
  }

  async function sendMessage() {
    if (!input.trim() || !userId) return;
    const content = input.trim();

    // Add user message immediately for UX
    const newMsg = { role: "user", content };
    setMessages((prev) => [...prev, newMsg]);
    setInput("");

    // Send to backend
    try {
      await fetch(`${process.env.NEXT_PUBLIC_API_BASE_URL}/chat`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ userId, message: content }),
      });

      // Start streaming assistant response
      startStreaming();
    } catch (err) {
      console.error("Error sending message:", err);
    }
  }

  // Allow pressing Enter to send
  function handleKeyDown(e) {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      sendMessage();
    }
  }

  // Listen for streaming messages via SSE
  function startStreaming() {
  if (eventSourceRef.current) {
    eventSourceRef.current.close();
    eventSourceRef.current = null;
  }

  setIsStreaming(true);
  const url = `${process.env.NEXT_PUBLIC_API_BASE_URL}/chat/stream/${userId}`;
  const es = new EventSource(url, { withCredentials: false });

  let partial = "";
  let inactivityTimeout;

  // Function to close the connection after 3 seconds of inactivity
  const closeConnectionAfterTimeout = () => {
    console.log("❌ Closing connection due to inactivity");
    es.close();
    setIsStreaming(false);
    eventSourceRef.current = null;
  };

  es.onmessage = (event) => {
    console.log("💬 Message event:", event.data);
    partial += event.data + " "; // Add space between tokens

    // Clear the previous timeout and set a new one whenever a new message is received
    clearTimeout(inactivityTimeout);
    inactivityTimeout = setTimeout(closeConnectionAfterTimeout, 3000); // 3 seconds timeout

    setMessages((prev) => {
      const updated = [...prev];
      if (updated.length && updated[updated.length - 1].role === "assistant") {
        updated[updated.length - 1].content = partial;
      } else {
        updated.push({ role: "assistant", content: partial });
      }
      return updated;
    });
  };

  es.addEventListener("done", () => {
    console.log("✅ Stream complete (done event)");
    es.close();
    setIsStreaming(false);
  });

  es.onerror = () => {
    console.error("❌ SSE connection error");
    es.close();
    eventSourceRef.current = null;
    setIsStreaming(false);
  };

  es.onopen = () => console.log("🟢 Connected to SSE stream");

  eventSourceRef.current = es;
}


  return (
    <div className="p-4 max-w-xl mx-auto">
      <h1 className="text-2xl font-bold mb-4 text-center">💬 Chat with AI</h1>

      <div className="border p-3 rounded-lg h-96 overflow-y-auto bg-gray-50 mb-4 shadow-inner prose prose-sm max-w-none">
        {messages.length > 0 ? (
          messages.map((m, i) => (
            <div key={i} className={`mb-2 ${m.role === "assistant" ? "text-blue-800" : "text-gray-800"}`}>
              <b>{m.role}:</b>{" "}
              <ReactMarkdown remarkPlugins={[remarkGfm]}>{m.content}</ReactMarkdown>
            </div>
          ))
        ) : (
          <p className="text-gray-400 text-center mt-20">No messages yet...</p>
        )}
      </div>

      <div className="flex gap-2">
        <textarea
          className="border flex-1 p-2 rounded focus:outline-none focus:ring resize-none h-20"
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder="Type a message... (Press Enter to send)"
          disabled={isStreaming}
        />
        <button
          onClick={sendMessage}
          className={`px-4 py-2 rounded text-white ${
            isStreaming ? "bg-gray-400" : "bg-blue-600 hover:bg-blue-700"
          }`}
          disabled={isStreaming}
        >
          {isStreaming ? "Generating..." : "Send"}
        </button>
      </div>
    </div>
  );
}
