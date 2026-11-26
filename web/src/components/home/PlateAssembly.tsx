/** An oblique projection of three cast plates and a grooved Olympic sleeve. */
export function PlateAssembly() {
  return (
    <svg className="home-plate" viewBox="0 0 500 250" fill="none" aria-hidden="true" focusable="false">
      <defs>
        <linearGradient id="iron-face" x1="-.75" y1="-.6" x2=".8" y2=".9" gradientUnits="objectBoundingBox" gradientTransform="translate(.48 .4) scale(.7)">
          <stop stopColor="#656a6d"/><stop offset=".24" stopColor="#45494c"/><stop offset=".59" stopColor="#303437"/><stop offset="1" stopColor="#171a1c"/>
        </linearGradient>
        <linearGradient id="iron-edge" x2=".85" y2="1"><stop stopColor="#7f8588"/><stop offset=".24" stopColor="#555a5e"/><stop offset=".55" stopColor="#272b2e"/><stop offset="1" stopColor="#111315"/></linearGradient>
        <linearGradient id="iron-bead" x1="0" y1="0" x2=".7" y2="1"><stop stopColor="#9da2a1"/><stop offset=".15" stopColor="#686e70"/><stop offset=".53" stopColor="#363a3b"/><stop offset="1" stopColor="#0e0f10"/></linearGradient>
        <linearGradient id="iron-steel" x1="0" y1="0" x2="0" y2="1"><stop stopColor="#818689"/><stop offset=".14" stopColor="#e0e4e5"/><stop offset=".28" stopColor="#aeb4b6"/><stop offset=".53" stopColor="#686e72"/><stop offset=".81" stopColor="#323739"/><stop offset="1" stopColor="#83898a"/></linearGradient>
        <pattern id="iron-grain" width="9" height="11" patternUnits="userSpaceOnUse">
          <path d="M1 3h.65m5 5h.7M2.2 10h.5" stroke="#e4e5dd" strokeOpacity=".18" strokeWidth=".65"/>
          <path d="M3 5h.8m3.8-4h.55" stroke="#030404" strokeOpacity=".43" strokeWidth=".75"/>
        </pattern>
        <path id="iron-letter-arc" d="M-69 -26A74 74 0 0 1 69 -26"/>
        <g id="iron-plate-body">
          <circle r="107" fill="url(#iron-edge)" stroke="#141719" strokeWidth="1.4"/>
          <circle r="104" stroke="url(#iron-bead)" strokeWidth="2"/>
        </g>
        <g id="iron-plate-face">
          <circle r="105.5" fill="url(#iron-face)" stroke="url(#iron-bead)" strokeWidth="2.3"/>
          <circle r="101.5" stroke="#17191b" strokeWidth="1.5"/>
          <circle r="98" fill="none" stroke="url(#iron-bead)" strokeWidth="4"/>
          <circle r="93" fill="url(#iron-grain)" stroke="#1d2022" strokeWidth="1.8"/>
          <circle r="88" stroke="#777c7b" strokeOpacity=".32"/>
          <circle r="47.8" stroke="url(#iron-bead)" strokeWidth="3"/>
          <circle r="42.6" stroke="#141619" strokeWidth="2"/>
          <circle r="40.7" stroke="#a0a3a1" strokeOpacity=".29"/>
          <circle r="24" fill="#171a1c" stroke="url(#iron-bead)" strokeWidth="3.3"/>
          <g fill="#969a97" stroke="#17191b" strokeWidth=".9" paintOrder="stroke" fontFamily="Arial Narrow, Arial, sans-serif" fontWeight="800" textAnchor="middle">
            <text fontSize="17" letterSpacing="3"><textPath href="#iron-letter-arc" startOffset="50%">IRONLOG</textPath></text>
            <text x="0" y="74" fontSize="18" letterSpacing="2">45 LB</text>
          </g>
          {[-1, 1].map(side => <g key={side} transform={`translate(${side * 73} 0) rotate(${side * 8})`}>
            <path d="M-3-12h6v24h-6z" fill="#25282a" stroke="#626665" strokeWidth="1.2"/>
            <path d="M-1-8v16" stroke="#141719" strokeWidth="1.25"/>
          </g>)}
        </g>
      </defs>
      <g transform="rotate(7 315 124)">
        <path className="home-rear-shaft" d="M7 90 211 114 211 141 7 117q-8-14 0-27Z" fill="url(#iron-steel)"/>
        <path className="home-rear-shaft" d="M8 93 195 115" stroke="#d4d7d6" strokeOpacity=".48"/>
        {[0, 1, 2].map((idx) => {
          const x = 202 + idx * 32, y = 106 + idx * 5
          return <g key={idx}>
            {[0, 4, 8, 12, 16].map((dx,i) => <g key={dx} transform={`matrix(.725 -.225 .16 .975 ${x+dx} ${y+dx*.13})`}>
              <use href="#iron-plate-body"/>{i > 0 && i < 4 && <circle r="106" stroke="#a4a7a7" strokeOpacity=".12" strokeWidth=".8"/>}
            </g>)}
            <g transform={`matrix(.725 -.225 .16 .975 ${x+18} ${y+2.4})`}><use href="#iron-plate-face"/></g>
          </g>
        })}
        <g transform="translate(283 118) rotate(7)">
          <path d="M-2-18 24-18Q36-14 36 0t-12 18H-2C-15 15-17-12-2-18Z" fill="url(#iron-steel)" stroke="#212425" strokeWidth="1.5"/>
          <ellipse cx="24" rx="12" ry="18" fill="#44494c" stroke="#a8acae" strokeWidth="1.2"/>
          <path d="M24-13.5H151q8 1 10 13.5t-10 13.5H24C14 11 12-10 24-13.5Z" fill="url(#iron-steel)" stroke="#262b2d"/>
          {Array.from({length:34},(_,i) => <path key={i} d={`M${32+i*3.45} -13.2q-8 11.4 0 26.4`} stroke="#14191a" strokeOpacity=".34" strokeWidth=".8"/>)}
          <ellipse cx="151" rx="9" ry="13.4" fill="#666d70" stroke="#b5babb"/>
          <ellipse cx="152" rx="6.5" ry="10" fill="#31363a" stroke="#3b4245"/>
          <path d="m149 0 4-3v6z" fill="#8c9394"/>
        </g>
      </g>
    </svg>
  )
}
