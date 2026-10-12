import type { ProblemDetail } from "../services/arenaService"
import { ConstraintList, Paragraphs } from "./Statement"

function ProblemStatement({ problem }: { problem: ProblemDetail }) {
  return (
    <article className="statement">
      <Paragraphs text={problem.statement} />

      <h3>Input</h3>
      <Paragraphs text={problem.inputFormat} />

      <h3>Output</h3>
      <Paragraphs text={problem.outputFormat} />

      {problem.constraints && (
        <>
          <h3>Limits</h3>
          <ConstraintList text={problem.constraints} />
        </>
      )}

      {problem.examples.map((example, index) => (
        <div className="example" key={index}>
          <h3>Example {index + 1}</h3>
          <div className="example__pair">
            <div>
              <span className="example__label">Input</span>
              <pre>{example.input}</pre>
            </div>
            <div>
              <span className="example__label">Output</span>
              <pre>{example.output}</pre>
            </div>
          </div>
        </div>
      ))}

      <p className="statement__note">
        Judged on {problem.testCount} tests, {problem.examples.length} of them shown above.
      </p>
    </article>
  )
}

export default ProblemStatement
