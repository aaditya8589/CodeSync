import { describe, expect, it } from "vitest"
import { formatClock, isPrivateNow, remainingMs, serverOffset } from "./clock"

describe("match clock", () => {
  it("corrects for a browser clock that is wrong", () => {
    // The server says 10:00:00 while this browser thinks it is 09:58:00
    const offset = serverOffset("2026-10-14T10:00:00Z", Date.parse("2026-10-14T09:58:00Z"))
    expect(offset).toBe(120_000)
    // The match ends at 10:30 server time; at 10:00 browser time 28 minutes are left, not 30
    expect(remainingMs("2026-10-14T10:30:00Z", offset, Date.parse("2026-10-14T10:00:00Z"))).toBe(28 * 60_000)
  })

  it("never shows negative time", () => {
    expect(remainingMs("2026-10-14T10:00:00Z", 0, Date.parse("2026-10-14T11:00:00Z"))).toBe(0)
  })

  it("formats minutes and hours, rounding seconds up so 0:00 means over", () => {
    expect(formatClock(0)).toBe("0:00")
    expect(formatClock(1)).toBe("0:01")
    expect(formatClock(307_000)).toBe("5:07")
    expect(formatClock(3_599_000)).toBe("59:59")
    expect(formatClock(3_600_000)).toBe("1:00:00")
  })

  it("files are private only until their time on the server's clock", () => {
    const now = Date.parse("2026-10-14T10:00:00Z")
    expect(isPrivateNow("2026-10-14T10:00:01Z", 0, now)).toBe(true)
    expect(isPrivateNow("2026-10-14T10:00:01Z", 2000, now)).toBe(false)
    expect(isPrivateNow(null, 0, now)).toBe(false)
  })
})
