// Shared navigation, motion preferences, and decorative photon flight.
document.documentElement.classList.add('js');
const motion=document.getElementById('motion');
const media=window.matchMedia('(prefers-reduced-motion: reduce)');
let paused=media.matches;
const scene=document.getElementById('photon-scene');
const flight=document.getElementById('flight');
const replay=document.getElementById('replay');
function applyMotion(){document.documentElement.classList.toggle('paused',paused);motion.textContent=paused?'Enable motion':'Pause motion';motion.setAttribute('aria-pressed',String(paused));for(const svg of [scene,document.getElementById('scientist-art')]){if(svg?.pauseAnimations){if(paused)svg.pauseAnimations();else svg.unpauseAnimations();}}}
motion.addEventListener('click',()=>{paused=!paused;applyMotion();});
media.addEventListener('change',()=>{paused=media.matches;applyMotion();});
applyMotion();
const menu=document.querySelector('.menu-toggle');
const nav=document.getElementById('navigation');
menu.addEventListener('click',()=>{const open=menu.getAttribute('aria-expanded')!=='true';menu.setAttribute('aria-expanded',String(open));menu.setAttribute('aria-label',open?'Close menu':'Open menu');nav.classList.toggle('open',open);});
document.addEventListener('keydown',e=>{if(e.key==='Escape'&&menu.getAttribute('aria-expanded')==='true'){menu.click();menu.focus();}});
nav.addEventListener('click',e=>{if(e.target.closest('a')&&menu.getAttribute('aria-expanded')==='true')menu.click();});
const current=location.pathname.split('/').pop()||'index.html';
nav.querySelectorAll('a').forEach(a=>{if(a.getAttribute('href')===current)a.setAttribute('aria-current','page');});
document.getElementById('year').textContent=new Date().getFullYear();
// The light is an artistic motif, not a physical simulation of a photon.
let flightTimer;
const photon=document.getElementById('photon');
let orbiting=false, orbitAngle=0, lastOrbitTime=0, orbitElapsed=0;
function orbitFrame(now){
 if(orbiting&&!paused&&!document.hidden){
  if(lastOrbitTime)orbitElapsed+=now-lastOrbitTime;
  scene.classList.toggle('energized',orbitElapsed>=2200);
  if(lastOrbitTime)orbitAngle+=(now-lastOrbitTime)*Math.PI*2/1400;
  photon.setAttribute('transform',`translate(${875+34*Math.sin(orbitAngle)} ${533+34*Math.cos(orbitAngle)})`);
 }
 lastOrbitTime=now;
 requestAnimationFrame(orbitFrame);
}
if(photon)requestAnimationFrame(orbitFrame);
function sendPhoton(){if(!scene||!flight)return;if(paused){replay.textContent='Enable motion to fly';clearTimeout(flightTimer);flightTimer=setTimeout(()=>replay.textContent='Send a photon ↗',2000);return;}clearTimeout(flightTimer);orbiting=false;orbitElapsed=0;scene.classList.remove('energized','eye-awake');document.querySelector('.portrait')?.classList.remove('landed');scene.classList.add('flying');document.getElementById('photon').removeAttribute('transform');flight.beginElement();replay.textContent='Light on its way…';}
if(replay){replay.addEventListener('click',sendPhoton);if(!paused)setTimeout(sendPhoton,700);}
if('IntersectionObserver' in window&&!media.matches){const observer=new IntersectionObserver(entries=>{entries.forEach(entry=>{if(entry.isIntersecting){entry.target.classList.remove('pending');observer.unobserve(entry.target);}});},{threshold:.08});document.querySelectorAll('.section-head,.archive-links,.timeline li,.interest-grid article').forEach(el=>{el.classList.add('reveal','pending');observer.observe(el);});}

if(flight)flight.addEventListener('endEvent',()=>{scene.classList.remove('flying');replay.textContent='Send another photon ↗';orbitAngle=0;orbitElapsed=0;lastOrbitTime=0;orbiting=true;scene.classList.add('eye-awake');photon.setAttribute('transform','translate(875 567)');});

const compact=matchMedia('(max-width:760px)');function placeMotion(){if(compact.matches)nav.appendChild(motion);else nav.after(motion);}compact.addEventListener('change',placeMotion);placeMotion();

// One gentle eyelid blink after the portrait has loaded, including refresh.
(function initPortraitBlink(){
  const art=document.getElementById('scientist-art');
  if(!art)return;
  const ns='http://www.w3.org/2000/svg';
  const layer=document.createElementNS(ns,'g');
  layer.setAttribute('aria-hidden','true');
  layer.style.pointerEvents='none';
  // Coordinates match the 1024-unit portrait; the subject's right eye is on the left.
  const eyes=[
    {a:[411,163],b:[457,178],u:[435,151],l:[433,178],skin:'#bbb7b0'},
    {a:[490,187],b:[532,201],u:[510,173],l:[510,205],skin:'#aaa79f'}
  ];
  const paths=eyes.map(e=>{
    const p=document.createElementNS(ns,'path');
    p.setAttribute('fill',e.skin);layer.appendChild(p);
    const lash=document.createElementNS(ns,'path');
    lash.setAttribute('fill','none');lash.setAttribute('stroke','#393735');
    lash.setAttribute('stroke-width','1.5');lash.setAttribute('stroke-linecap','round');
    layer.appendChild(lash);return {e,p,lash};
  });
  function draw(t){
    paths.forEach(({e,p,lash})=>{
      const y=e.u[1]+(e.l[1]-e.u[1])*t;
      const x=e.u[0]+(e.l[0]-e.u[0])*t;
      const start='M '+e.a.join(' ');
      const edge=' Q '+x+' '+y+' '+e.b.join(' ');
      p.setAttribute('d',start+' Q '+e.u.join(' ')+' '+e.b.join(' ')+' Q '+x+' '+y+' '+e.a.join(' ')+' Z');
      lash.setAttribute('d',start+edge);
    });
  }
  function blink(){
    if(paused||media.matches||document.hidden)return;
    art.appendChild(layer);let start;
    function frame(now){
      if(paused||media.matches||document.hidden){layer.remove();return;}
      start??=now;const elapsed=now-start;
      const amount=elapsed<120?elapsed/120:elapsed<170?1:Math.max(0,1-(elapsed-170)/160);
      draw(amount);
      if(elapsed<330)requestAnimationFrame(frame);else layer.remove();
    }
    requestAnimationFrame(frame);
  }
  function schedule(){setTimeout(blink,900);}
  if(document.readyState==='complete')schedule();
  else window.addEventListener('load',schedule,{once:true});
})();

// Close More with Escape, an outside click, or a destination selection.
const more=document.querySelector('.nav-more');
document.addEventListener('click',e=>{if(more&&!more.contains(e.target))more.open=false;});
more?.querySelector('a')?.addEventListener('click',()=>{more.open=false;});
document.addEventListener('keydown',e=>{if(e.key==='Escape'&&more?.open){more.open=false;more.querySelector('summary').focus();}});
