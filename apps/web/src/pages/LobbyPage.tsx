import { FormEvent, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";

const randomRoomId = (): string => Math.random().toString(36).slice(2, 8);

export function LobbyPage(): JSX.Element {
  const navigate = useNavigate();
  const [displayName, setDisplayName] = useState("");
  const [roomId, setRoomId] = useState("");
  const placeholderRoomId = useMemo(() => randomRoomId(), []);

  const onSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const finalDisplayName = displayName.trim() || "Anonymous";
    const finalRoomId = (roomId.trim() || placeholderRoomId).toLowerCase();
    navigate(`/room/${finalRoomId}?name=${encodeURIComponent(finalDisplayName)}`);
  };

  return (
    <main className="page">
      <section className="card">
        <h1>Echo Room</h1>
        <p>Join or create a voice room (max 5 participants).</p>
        <form onSubmit={onSubmit} className="form">
          <label>
            Display Name
            <input
              value={displayName}
              onChange={(event) => setDisplayName(event.target.value)}
              placeholder="Your name"
              maxLength={32}
            />
          </label>
          <label>
            Room ID
            <input
              value={roomId}
              onChange={(event) => setRoomId(event.target.value)}
              placeholder={placeholderRoomId}
              maxLength={24}
            />
          </label>
          <button type="submit">Enter Room</button>
        </form>
      </section>
    </main>
  );
}
