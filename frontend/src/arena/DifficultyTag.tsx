function DifficultyTag({ difficulty }: { difficulty: string }) {
  return <span className={`difficulty difficulty--${difficulty.toLowerCase()}`}>{difficulty}</span>
}

export default DifficultyTag
