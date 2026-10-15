import { useNavigate, useSearchParams } from "react-router-dom"
import TopBar from "../components/TopBar"
import NewMatchForm from "../match/NewMatchForm"
import { createMatch } from "../services/matchService"

function NewMatch() {
  const navigate = useNavigate()
  const [params] = useSearchParams()

  const create = async (slugs: string[], minutes: number) => {
    const created = await createMatch(slugs, minutes)
    navigate(`/rooms/${created.roomId}`)
  }

  return (
    <>
      <TopBar title="New match" />
      <div className="narrow-page">
        <NewMatchForm onCreate={create} initialSlug={params.get("problem") ?? undefined} />
      </div>
    </>
  )
}

export default NewMatch
