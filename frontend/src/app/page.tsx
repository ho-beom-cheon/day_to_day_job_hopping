import { redirect } from "next/navigation";

/** No landing page: the dashboard asks for Google login when there is no session. */
export default function Home() { redirect("/workspace"); }
