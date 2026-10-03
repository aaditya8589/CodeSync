interface OutputPanelProps {
  output: string
}

function OutputPanel({ output }: OutputPanelProps) {
  return (
    <section>
      <h3>Output</h3>

      <pre>
        {output || "No output yet."}
      </pre>
    </section>
  )
}

export default OutputPanel
