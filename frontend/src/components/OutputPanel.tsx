import type { ExecutionResult, ExecutionStatus } from "../services/executionService"

export type RunState =
  | { kind: "idle" }
  | { kind: "running" }
  | { kind: "failed"; message: string }
  | { kind: "done"; result: ExecutionResult }

const VERDICT: Record<ExecutionStatus, { text: string; tone: "ok" | "err" | "limit" }> = {
  SUCCESS: { text: "Finished", tone: "ok" },
  COMPILE_ERROR: { text: "Compilation error", tone: "err" },
  RUNTIME_ERROR: { text: "Runtime error", tone: "err" },
  TIME_LIMIT_EXCEEDED: { text: "Time limit exceeded", tone: "limit" },
  MEMORY_LIMIT_EXCEEDED: { text: "Memory limit exceeded (256 MB)", tone: "limit" },
  INTERNAL_ERROR: { text: "Couldn't run the code", tone: "err" },
}

function OutputPanel({ state }: { state: RunState }) {
  const verdict = state.kind === "done" ? VERDICT[state.result.status] : null

  return (
    <section className="console__pane" aria-label="Output">
      <div className="console__head" aria-live="polite">
        <span>Output</span>
        {state.kind === "running" && <span>Compiling and running...</span>}
        {verdict && state.kind === "done" && (
          <span className={`verdict verdict--${verdict.tone}`}>
            {verdict.text}{" "}
            <span className="verdict__time">
              {state.result.durationMs} ms
              {state.result.status !== "SUCCESS" && state.result.exitCode !== null
                ? `, exit code ${state.result.exitCode}`
                : ""}
            </span>
          </span>
        )}
      </div>

      <pre className="console__output">
        {state.kind === "idle" && <span className="console__note">Run the file to see its output here.</span>}
        {state.kind === "failed" && <span className="console__stderr">{state.message}</span>}
        {state.kind === "done" && (
          <>
            {state.result.stdout}
            {state.result.stderr && (
              <span className="console__stderr">
                {state.result.stdout && !state.result.stdout.endsWith("\n") ? "\n" : ""}
                {state.result.stderr}
              </span>
            )}
            {!state.result.stdout && !state.result.stderr && (
              <span className="console__note">The program printed nothing.</span>
            )}
            {state.result.outputTruncated && (
              <span className="console__note">{"\n"}Output was cut off at 64 KB.</span>
            )}
          </>
        )}
      </pre>
    </section>
  )
}

export default OutputPanel
