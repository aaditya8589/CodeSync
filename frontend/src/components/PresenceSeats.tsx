import type { PresenceMember } from "../hooks/useCodeSync"
import { colorFor } from "../presence/colors"

interface PresenceSeatsProps {
  members: PresenceMember[]
  me: string | null
}

// Each person appears in the colour of their cursor, so a caret in the editor can be
// matched to a name at a glance
function PresenceSeats({ members, me }: PresenceSeatsProps) {
  return (
    <ul className="seats" aria-label="People in this room">
      {members.map((member) => {
        const tabs = member.clientIds.length
        const label = member.username === me ? `${member.username} (you)` : member.username
        return (
          <li
            key={member.username}
            className="seat"
            style={{ "--who": colorFor(member.username) } as React.CSSProperties}
            title={tabs > 1 ? `${label}, ${tabs} tabs` : label}
          >
            <span className="seat__mark" aria-hidden="true">
              {member.username.charAt(0).toUpperCase()}
            </span>
            <span className="seat__name">{label}</span>
            {tabs > 1 && <span className="seat__tabs">{tabs} tabs</span>}
          </li>
        )
      })}
    </ul>
  )
}

export default PresenceSeats
