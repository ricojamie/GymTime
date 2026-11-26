import { useEffect, useRef, type CSSProperties } from 'react'
import './plate-motion.css'

const plateSeats = [
  { name: 'inner', x: 171 },
  { name: 'middle', x: 200 },
  { name: 'outer', x: 229 },
] as const

const sparkVectors = [
  { sx: '-18px', sy: '-26px', r: '2.2' },
  { sx: '14px', sy: '-30px', r: '1.8' },
  { sx: '-24px', sy: '14px', r: '1.6' },
  { sx: '20px', sy: '22px', r: '2.0' },
  { sx: '-8px', sy: '-36px', r: '1.4' },
  { sx: '26px', sy: '-10px', r: '1.5' },
] as const

/** The plates move parallel to one sleeve. The projection turns the whole mechanism together. */
export function PlateAssembly({ gatorMode = false }: { gatorMode?: boolean }) {
  const assembly = useRef<SVGSVGElement>(null)
  useEffect(() => {
    const svg = assembly.current
    const load = svg?.querySelector('.iron-load-flex')
    if (!svg || !load) return
    let preparing = svg.hasAttribute('data-intro-preparing')
    let paintFrame: number | undefined
    let fontDeadline: number | undefined
    const stopPreparation = () => {
      preparing = false
      window.clearTimeout(fontDeadline)
      if (paintFrame !== undefined) window.cancelAnimationFrame(paintFrame)
    }
    const begin = () => {
      if (!preparing) return
      stopPreparation()
      svg.removeAttribute('data-intro-preparing')
    }
    const allowFirstPaint = () => {
      if (!preparing || paintFrame !== undefined) return
      paintFrame = window.requestAnimationFrame(() => {
        paintFrame = window.requestAnimationFrame(begin)
      })
    }
    // Let the initial pose paint before starting the clock, even during an offline font fallback.
    fontDeadline = window.setTimeout(allowFirstPaint, 500)
    if (document.fonts) void document.fonts.ready.then(allowFirstPaint, allowFirstPaint)
    else allowFirstPaint()
    const replay = () => {
      if (document.documentElement.dataset.motion === 'reduced' ||
        window.matchMedia?.('(prefers-reduced-motion: reduce)').matches) return
      if (preparing) {
        begin()
        return
      }
      // Flush only the animation name. All castings, lettering, and material instances stay mounted.
      svg.classList.add('iron-rewind')
      void getComputedStyle(load).animationName
      svg.classList.remove('iron-rewind')
    }
    window.addEventListener('ironlog:replay-plates', replay)
    return () => {
      stopPreparation()
      window.removeEventListener('ironlog:replay-plates', replay)
    }
  }, [])

  return (
    <svg
      ref={assembly}
      className={`home-plate ${gatorMode ? 'is-gator-plates' : ''}`}
      data-intro-preparing=""
      viewBox="0 0 500 250"
      fill="none"
      aria-hidden="true"
      focusable="false"
    >
      <defs>
        <linearGradient id="iron-face" x1="-.75" y1="-.6" x2=".8" y2=".9" gradientUnits="objectBoundingBox" gradientTransform="translate(.48 .4) scale(.7)">
          <stop stopColor="#656a6d"/><stop offset=".24" stopColor="#45494c"/><stop offset=".59" stopColor="#303437"/><stop offset="1" stopColor="#171a1c"/>
        </linearGradient>
        <linearGradient id="iron-edge" x2=".85" y2="1"><stop stopColor="#7f8588"/><stop offset=".24" stopColor="#555a5e"/><stop offset=".55" stopColor="#272b2e"/><stop offset="1" stopColor="#111315"/></linearGradient>
        <linearGradient id="iron-bead" x1="0" y1="0" x2=".7" y2="1"><stop stopColor="#9da2a1"/><stop offset=".15" stopColor="#686e70"/><stop offset=".53" stopColor="#363a3b"/><stop offset="1" stopColor="#0e0f10"/></linearGradient>
        <linearGradient id="iron-steel" x1="0" y1="0" x2="0" y2="1"><stop stopColor="#818689"/><stop offset=".14" stopColor="#e0e4e5"/><stop offset=".28" stopColor="#aeb4b6"/><stop offset=".53" stopColor="#686e72"/><stop offset=".81" stopColor="#323739"/><stop offset="1" stopColor="#83898a"/></linearGradient>
        <linearGradient id="iron-sheen-grad" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stopColor="white" stopOpacity="0"/>
          <stop offset=".45" stopColor="white" stopOpacity="0"/>
          <stop offset=".5" stopColor="white" stopOpacity=".24"/>
          <stop offset=".55" stopColor="white" stopOpacity="0"/>
          <stop offset="1" stopColor="white" stopOpacity="0"/>
        </linearGradient>
        <pattern id="iron-grain" width="9" height="11" patternUnits="userSpaceOnUse">
          <path d="M1 3h.65m5 5h.7M2.2 10h.5" stroke="#e4e5dd" strokeOpacity=".18" strokeWidth=".65"/>
          <path d="M3 5h.8m3.8-4h.55" stroke="#030404" strokeOpacity=".43" strokeWidth=".75"/>
        </pattern>
        <pattern id="iron-knurl" width="5" height="5" patternUnits="userSpaceOnUse">
          <path d="m-1 1 5 5m0-5 2 2m-1-4-7 7m7-2-2 2" stroke="#1c2124" strokeOpacity=".48" strokeWidth=".8"/>
        </pattern>
        <linearGradient id="iron-shaft-light" x1="4" x2="52" y1="0" y2="0" gradientUnits="userSpaceOnUse">
          <stop stopColor="white" stopOpacity="0"/><stop offset="1" stopColor="white"/>
        </linearGradient>
        <mask id="iron-shaft-fade" maskUnits="userSpaceOnUse" x="-13" y="108" width="74" height="24"><rect x="-13" y="108" width="74" height="24" fill="url(#iron-shaft-light)"/></mask>
        <pattern id="iron-flutes" width="4.2" height="32" patternUnits="userSpaceOnUse" x="0" y="-16">
          <path d="M4 2Q-3 16 4 30" stroke="#161a1c" strokeOpacity=".38" strokeWidth="1"/>
          <path d="M5.2 2Q-1.8 16 5.2 30" stroke="#e1e4e3" strokeOpacity=".26" strokeWidth=".65"/>
        </pattern>
        <linearGradient id="iron-clamp" x1="0" x2=".12" y1="0" y2="1">
          <stop stopColor="#abb0b0"/><stop offset=".18" stopColor="#5c6467"/><stop offset=".46" stopColor="#343a3c"/><stop offset=".79" stopColor="#171c1f"/><stop offset="1" stopColor="#596063"/>
        </linearGradient>
        <clipPath id="iron-sleeve-end"><path d="M0 105h477v30H0Z"/></clipPath>
        <clipPath id="iron-plate-clip"><circle r="105"/></clipPath>
        <path id="iron-letter-arc" d="M-69 -26A74 74 0 0 1 69 -26"/>
        <g id="iron-plate-body">
          <circle r="107" fill="url(#iron-edge)" stroke="#141719" strokeWidth="1.4"/>
          <circle r="104" stroke="url(#iron-bead)" strokeWidth="2"/>
          {gatorMode && <circle r="105" stroke="#FA4616" strokeOpacity=".55" strokeWidth="2.4" strokeDasharray="34 22"/>}
        </g>
        <g id="iron-plate-face">
          <circle r="105.5" fill="url(#iron-face)" stroke="url(#iron-bead)" strokeWidth="2.3"/>
          <circle r="101.5" stroke="#17191b" strokeWidth="1.5"/>
          <circle r="98" fill="none" stroke={gatorMode ? '#0021A5' : 'url(#iron-bead)'} strokeWidth="4"/>
          <circle r="93" fill="url(#iron-grain)" stroke="#1d2022" strokeWidth="1.8"/>
          <circle r="88" stroke={gatorMode ? '#FA4616' : '#777c7b'} strokeOpacity={gatorMode ? '.68' : '.32'} strokeWidth={gatorMode ? '2' : '1'}/>
          <circle r="47.8" stroke={gatorMode ? '#FA4616' : 'url(#iron-bead)'} strokeWidth="3"/>
          <circle r="42.6" stroke="#141619" strokeWidth="2"/>
          <circle r="40.7" stroke="#a0a3a1" strokeOpacity=".29"/>
          <circle r="24" fill="#171a1c" stroke="url(#iron-bead)" strokeWidth="3.3"/>
          <g fill={gatorMode ? '#f2f5ff' : '#b1b5b0'} stroke="#131719" strokeWidth="1.8" paintOrder="stroke" fontFamily="Arial Narrow, Arial, sans-serif" fontWeight="800" textAnchor="middle">
            <text fontSize="18" letterSpacing="3"><textPath href="#iron-letter-arc" startOffset="50%">{gatorMode ? 'SWAMP' : 'IRONLOG'}</textPath></text>
            <text x="0" y="74" fontSize="24" letterSpacing="1">45<tspan dx="2" fontSize="13">LB</tspan></text>
          </g>
          <path d="M-75-34a82 82 0 0 1 15-23m120 112a82 82 0 0 1-14 12" stroke="#b7bcba" strokeOpacity=".48" strokeWidth="2"/>
          {[-1, 1].map(side => <g key={side} transform={`translate(${side * 73} 0) rotate(${side * 8})`}>
            <path d="M-3-12h6v24h-6z" fill={gatorMode ? '#0021A5' : '#25282a'} stroke={gatorMode ? '#FA4616' : '#626665'} strokeWidth="1.2"/>
            <path d="M-1-8v16" stroke="#141719" strokeWidth="1.25"/>
          </g>)}
          <g clipPath="url(#iron-plate-clip)">
            <rect className="iron-plate-sheen" x="-140" y="-120" width="280" height="240" fill="url(#iron-sheen-grad)"/>
          </g>
        </g>
        <g id="iron-cast">
          {[0, 4, 9, 14].map(depth => <g key={depth} transform={`matrix(.58 -.18 .15 .99 ${depth} 0)`}>
            <use href="#iron-plate-body"/>
          </g>)}
          <g transform="matrix(.58 -.18 .15 .99 17 0)"><use href="#iron-plate-face"/></g>
        </g>
        <path id="iron-exposed-sleeve" d="M0-13H82v26H0C-12 13-12-13 0-13Z"/>
        <g id="iron-chrome-lip">
          <use href="#iron-exposed-sleeve" fill="url(#iron-steel)"/>
          <use href="#iron-exposed-sleeve" fill="url(#iron-flutes)"/>
          <path d="M82-13H0C-12-13-12 13 0 13h82" stroke="#42494b"/>
        </g>
      </defs>
      <g transform="rotate(11 251 125)">
        <g className="home-rear-shaft" mask="url(#iron-shaft-fade)">
          <path d="M-12 111H57v18h-69Z" fill="url(#iron-steel)"/>
          <path d="M-12 111H57v18h-69Z" fill="url(#iron-knurl)"/>
        </g>
        <g className="iron-load-flex">
          <g className="home-rear-shaft">
            <path d="M50 111h117v18H50Z" fill="url(#iron-steel)"/>
            <path d="M50 111h76v18H50Z" fill="url(#iron-knurl)"/>
            <path d="M156 98h13v44h-13q-12-22 0-44Z" fill="url(#iron-clamp)" stroke="#383d3f"/>
          </g>
          <g transform="translate(170 120)">
            <path d="M0-13h307v26H0Z" fill="url(#iron-steel)"/>
            <path d="M0-13h307v26H0Z" fill="url(#iron-flutes)"/>
            <path d="M0-13h307M0 13h307" stroke="#42494b"/>
          </g>
          {plateSeats.map(({name, x}) => <g key={name} className={`iron-slide iron-slide--${name}`}>
            <use href="#iron-cast" transform={`translate(${x} 120)`}/>
            {name !== 'outer' && <use href="#iron-chrome-lip" transform={`translate(${x + 17} 120)`}/>}
          </g>)}
          {/* Shockwave impact rings and chalk-dust sparks anchored at each plate seat */}
          {plateSeats.map(({name, x}) => (
            <g key={`fx-${name}`} transform={`translate(${x + 16} 120)`}>
              <ellipse className={`iron-shockwave iron-shockwave--${name}`} rx="44" ry="78" fill="none" strokeWidth="2.4"/>
              {sparkVectors.map((v, idx) => (
                <circle
                  key={idx}
                  className={`iron-spark iron-spark--${name}`}
                  cx="0"
                  cy={idx % 2 === 0 ? -36 : 36}
                  r={v.r}
                  style={{ '--sx': v.sx, '--sy': v.sy, '--spark-delay': `${idx * 18}ms` } as CSSProperties}
                />
              ))}
            </g>
          ))}
          {/* The front plate can reach the end of the fixed sleeve while it is loading. */}
          <g clipPath="url(#iron-sleeve-end)">
            <g className="iron-slide iron-slide--outer"><use href="#iron-chrome-lip" transform="translate(246 120)"/></g>
          </g>
          <ellipse cx="477" cy="120" rx="7.7" ry="13.1" fill="#868e90" stroke="#c2c8c8"/>
          <ellipse cx="477" cy="120" rx="5.7" ry="10.5" fill="#2a3033" stroke="#484e50"/>
          <path d="m475 119 4-2v6l-4-2z" fill="#9aa3a3"/>
          <g className="iron-slide iron-slide--collar">
            <g transform="translate(253 120)">
              <path d="M0-22h23l4 4v36l-5 4H0c-16-4-16-40 0-44Z" fill="url(#iron-clamp)" stroke="#171c1e" strokeWidth="2"/>
              <path d="M5-19C-6-12-6 12 5 18m5-37C-2-12-2 12 10 18" stroke="#a2a7a8" strokeOpacity=".53" strokeWidth="1.2"/>
              <path d="M21-22c-17 2-17 42 0 44 11 0 14-13 13-24-1-12-5-20-13-20Zm1 8c6 0 9 28 0 28-8 0-9-26 0-28Z" fill="url(#iron-clamp)" fillRule="evenodd" stroke="#919797" strokeWidth="1"/>
              <g transform="translate(9 -20)">
                <g className="iron-clamp-lever">
                  <path d="m-2-1 17-6 9 1 1 4-6 4-18 3z" fill="#333a3e" stroke="#949b9c" strokeWidth="1.4" strokeLinejoin="round"/>
                  <path className="iron-clamp-index" d="m12-4 7-2" strokeWidth="2.4" strokeLinecap="round"/>
                </g>
                <circle r="3" fill="#8a9393" stroke="#1c2224" strokeWidth="1.4"/>
              </g>
            </g>
          </g>
          {/* Collar lock snap flash */}
          <g transform="translate(268 120)">
            <ellipse className="iron-collar-flash" rx="22" ry="38" fill="none" strokeWidth="3"/>
          </g>
        </g>
      </g>
    </svg>
  )
}
