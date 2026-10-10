import type { ReactNode } from "react"
import { colorFor } from "../presence/colors"

// Two people in the same file, which is what the product does, shown before anyone signs in
function EditorPreview() {
  const maya = { "--who": colorFor("maya") } as React.CSSProperties
  const arjun = { "--who": colorFor("arjun") } as React.CSSProperties

  return (
    <figure className="preview" aria-label="Two people editing two_sum.cpp at the same time">
      <div className="preview__tab">two_sum.cpp</div>
      <pre className="preview__code">
        <span className="n">1</span><span className="k">#include</span> <span className="s">&lt;bits/stdc++.h&gt;</span>{"\n"}
        <span className="n">2</span><span className="k">using namespace</span> std;{"\n"}
        <span className="n">3</span>{"\n"}
        <span className="n">4</span><span className="k">int</span> main() {"{"}{"\n"}
        <span className="n">5</span>    <span className="k">int</span> n, target;{"\n"}
        <span className="n">6</span>    unordered_map&lt;<span className="k">int</span>, <span className="k">int</span>&gt; <span className="preview__sel" style={maya}>seen</span><span className="preview__caret" style={maya} data-name="maya" />;{"\n"}
        <span className="n">7</span>    <span className="c">// one pass</span>{"\n"}
        <span className="n">8</span>    <span className="k">for</span> (<span className="k">int</span> i = 0; i &lt; n; i++) {"{"}<span className="preview__caret" style={arjun} data-name="arjun" />{"\n"}
        <span className="n">9</span>{"\n"}
        <span className="n">10</span>{"}"}
      </pre>
    </figure>
  )
}

function AuthLayout({ children }: { children: ReactNode }) {
  return (
    <div className="auth">
      <section className="auth__showcase">
        <span className="brand">CodeSync</span>
        <h1 className="auth__headline">Write code together, line by line.</h1>
        <p className="auth__lead">
          Shared rooms with live cursors, version history, and a sandbox that runs C++, Python
          and Java for everyone in the room.
        </p>
        <EditorPreview />
      </section>
      <main className="auth__panel">{children}</main>
    </div>
  )
}

export default AuthLayout
