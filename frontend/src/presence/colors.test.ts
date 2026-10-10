import { describe, expect, it } from "vitest"
import { colorClassRules, colorFor, colorIndex, PRESENCE_COLORS } from "./colors"

describe("colorFor", () => {
  it("is stable for a username", () => {
    expect(colorFor("alice")).toBe(colorFor("alice"))
  })

  it("always returns a palette colour", () => {
    for (const name of ["", "a", "bob", "Aaditya", "x".repeat(500), "ünïcødé"]) {
      expect(PRESENCE_COLORS).toContain(colorFor(name))
    }
  })

  it("spreads users across the palette", () => {
    const used = new Set(Array.from({ length: 200 }, (_, i) => colorFor(`user${i}`)))
    expect(used.size).toBe(PRESENCE_COLORS.length)
  })
})

describe("colorClassRules", () => {
  it("defines the class colorIndex points to, with that user's colour", () => {
    const rules = colorClassRules()
    for (const name of ["alice", "bob", "carol"]) {
      expect(rules).toContain(`.remote-color-${colorIndex(name)} { --remote-color: ${colorFor(name)}; }`)
    }
  })
})
