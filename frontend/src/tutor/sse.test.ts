import { describe, expect, it } from "vitest"
import { SseParser } from "./sse"

describe("SseParser", () => {
  it("reads events split across chunks at any point", () => {
    const stream = 'data: {"text":"Hel"}\n\ndata: {"text":"lo"}\n\nevent: done\ndata: {"codeRemoved":false}\n\n'
    for (let cut = 1; cut < stream.length; cut++) {
      const parser = new SseParser()
      const events = [...parser.push(stream.slice(0, cut)), ...parser.push(stream.slice(cut))]
      expect(events).toEqual([
        { event: "message", data: '{"text":"Hel"}' },
        { event: "message", data: '{"text":"lo"}' },
        { event: "done", data: '{"codeRemoved":false}' },
      ])
    }
  })

  it("handles CRLF, comments and multi-line data", () => {
    const parser = new SseParser()
    expect(parser.push(": keep-alive\r\n\r\nevent: error\r\ndata: a\r\ndata: b\r\n\r\n")).toEqual([
      { event: "error", data: "a\nb" },
    ])
  })

  it("keeps an unfinished event for later", () => {
    const parser = new SseParser()
    expect(parser.push('data: {"text":"x"}\n')).toEqual([])
    expect(parser.push("\n")).toEqual([{ event: "message", data: '{"text":"x"}' }])
  })
})
