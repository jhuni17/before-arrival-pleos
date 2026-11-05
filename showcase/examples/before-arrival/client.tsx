import { createRoot } from 'react-dom/client'
import { Showcase } from './index'

const state = new URLSearchParams(location.search).get('state')
createRoot(document.getElementById('root')!).render(<Showcase state={state}/>)
