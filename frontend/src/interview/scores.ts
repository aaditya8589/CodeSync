// The four areas an interviewer scores, from 1 (weak) to 4 (strong)
export const SCORE_AREAS: { key: "problemSolving" | "code" | "communication" | "complexity"; label: string; hint: string }[] = [
  { key: "problemSolving", label: "Problem solving", hint: "Understood the problem, found an approach, handled edge cases" },
  { key: "code", label: "Code quality", hint: "Correct, readable, sensible names and structure" },
  { key: "communication", label: "Communication", hint: "Thought out loud, asked questions, took hints well" },
  { key: "complexity", label: "Complexity analysis", hint: "Knew the time and space cost and how to improve it" },
]

export const SCORE_TEXT = ["", "Weak", "Mixed", "Good", "Strong"]
