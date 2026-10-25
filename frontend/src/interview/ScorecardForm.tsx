import { useState, type FormEvent } from "react"
import { RECOMMENDATION_TEXT, type Recommendation, type Scorecard } from "../services/interviewService"
import { SCORE_AREAS, SCORE_TEXT } from "./scores"

type Card = Omit<Scorecard, "at">

interface ScorecardFormProps {
  initial: Scorecard | null
  onSave: (card: Card) => Promise<void>
}

// The interviewer's verdict once time is up: four areas from 1 to 4, a recommendation, feedback
function ScorecardForm({ initial, onSave }: ScorecardFormProps) {
  const [card, setCard] = useState<Partial<Card>>(() => initial ?? { feedback: "" })
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState("")
  const [saved, setSaved] = useState(Boolean(initial))

  const complete = SCORE_AREAS.every(({ key }) => card[key]) && Boolean(card.recommendation)

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    if (!complete) return
    setBusy(true)
    setError("")
    try {
      await onSave(card as Card)
      setSaved(true)
    } catch (reason) {
      setError((reason instanceof Error && reason.message) || "Could not save the scorecard")
    } finally {
      setBusy(false)
    }
  }

  const change = (patch: Partial<Card>) => {
    setCard((current) => ({ ...current, ...patch }))
    setSaved(false)
  }

  return (
    <form className="scorecard-form" onSubmit={(event) => void submit(event)} aria-label="Scorecard">
      {SCORE_AREAS.map(({ key, label, hint }) => (
        <fieldset key={key} className="scorecard-form__area">
          <legend title={hint}>{label}</legend>
          <div className="scorecard-form__scale">
            {[1, 2, 3, 4].map((value) => (
              <label key={value} className={`score-chip${card[key] === value ? " score-chip--on" : ""}`}>
                <input type="radio" name={key} value={value} checked={card[key] === value}
                       onChange={() => change({ [key]: value } as Partial<Card>)} />
                {value} <small>{SCORE_TEXT[value]}</small>
              </label>
            ))}
          </div>
        </fieldset>
      ))}

      <label className="scorecard-form__field">
        <span>Recommendation</span>
        <select className="input" value={card.recommendation ?? ""}
                onChange={(event) => change({ recommendation: event.target.value as Recommendation })}>
          <option value="" disabled>Choose...</option>
          {(Object.keys(RECOMMENDATION_TEXT) as Recommendation[]).map((key) => (
            <option key={key} value={key}>{RECOMMENDATION_TEXT[key]}</option>
          ))}
        </select>
      </label>

      <label className="scorecard-form__field">
        <span>Feedback for the candidate</span>
        <textarea className="input" rows={4} maxLength={4000} value={card.feedback ?? ""}
                  placeholder="What went well, and the one thing to practise next"
                  onChange={(event) => change({ feedback: event.target.value })} />
      </label>

      {error && <p className="message message--error" role="alert">{error}</p>}
      <button type="submit" className="button button--primary" disabled={busy || !complete}>
        {busy ? "Saving..." : saved ? "Saved: update" : "Send feedback"}
      </button>
    </form>
  )
}

export default ScorecardForm
