export const PRESENCE_COLORS = [
  "#e06c75",
  "#61afef",
  "#98c379",
  "#e5c07b",
  "#c678dd",
  "#56b6c2",
  "#d19a66",
  "#ff79c6",
]

// The same user gets the same colour in every tab and on every machine
export function colorIndex(username: string): number {
  let hash = 0
  for (let i = 0; i < username.length; i++) {
    hash = (hash * 31 + username.charCodeAt(i)) | 0
  }
  return Math.abs(hash) % PRESENCE_COLORS.length
}

export function colorFor(username: string): string {
  return PRESENCE_COLORS[colorIndex(username)]
}

// Monaco decorations can only be styled through class names, so each colour gets a class
export function colorClassRules(): string {
  return PRESENCE_COLORS
    .map((color, index) => `.remote-color-${index} { --remote-color: ${color}; }`)
    .join("\n")
}
