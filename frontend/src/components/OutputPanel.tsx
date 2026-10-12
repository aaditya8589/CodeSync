import type { ExecutionResult, ExecutionStatus } from "../services/executionService"
import { VERDICT_TEXT, verdictTone, type JudgeResult } from "../services/arenaService"

export type RunState =
  | { kind: "idle" }
  | { kind: "running" }
  | { kind: "failed"; message: string }
  | { kind: "done"; result: ExecutionResult }
  | { kind: "judging" }
  | { kind: "judged"; result: JudgeResult }

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
        {state.kind === "judging" && <span>Judging on all tests...</span>}
        {state.kind === "judged" && (
          <span className={`verdict verdict--${verdictTone(state.result.verdict)}`}>
            {VERDICT_TEXT[state.result.verdict]}
            {state.result.failedTest !== null ? ` on test ${state.result.failedTest}` : ""}{" "}
            <span className="verdict__time">
              {state.result.passed} of {state.result.total} tests passed
            </span>
          </span>
        )}
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

      {state.kind === "judged" ? (
        <JudgeReport result={state.result} />
      ) : (
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
      )}
    </section>
  )
}

function JudgeReport({ result }: { result: JudgeResult }) {
  // The first failing example is worth showing in full; hidden tests only give their verdict
  const failedSample = result.tests.find((test) => test.sample && test.verdict !== "ACCEPTED")

  return (
    <div className="console__output judge">
      {result.compileOutput && <pre className="console__stderr">{result.compileOutput}</pre>}

      {result.tests.length > 0 && (
        <ol className="judge__tests">
          {result.tests.map((test) => (
            <li key={test.number} className={`judge__test judge__test--${verdictTone(test.verdict)}`}
                title={`Test ${test.number}: ${VERDICT_TEXT[test.verdict]}, ${test.timeMs} ms`}>
              <span className="judge__number">{test.number}</span>
              <span className="judge__verdict">{test.verdict === "ACCEPTED" ? "Passed" : VERDICT_TEXT[test.verdict]}</span>
              <span className="judge__time">{test.timeMs} ms{test.sample ? ", example" : ""}</span>
            </li>
          ))}
        </ol>
      )}

      {failedSample && (
        <div className="judge__detail">
          <p>Example test {failedSample.number}</p>
          <div className="example__pair">
            <div><span className="example__label">Input</span><pre>{failedSample.input}</pre></div>
            <div><span className="example__label">Expected</span><pre>{failedSample.expectedOutput}</pre></div>
            <div><span className="example__label">Your output</span><pre>{failedSample.actualOutput || " "}</pre></div>
          </div>
          {failedSample.errorOutput && <pre className="console__stderr">{failedSample.errorOutput}</pre>}
        </div>
      )}
    </div>
  )
}

export default OutputPanel
