// Shared navigation, motion preferences, and decorative photon flight.
document.documentElement.classList.add('js');
const motion=document.getElementById('motion');
const media=window.matchMedia('(prefers-reduced-motion: reduce)');
let paused=media.matches;
const scene=document.getElementById('photon-scene');
const flight=document.getElementById('flight');
const replay=document.getElementById('replay');
function applyMotion(){document.documentElement.classList.toggle('paused',paused);motion.textContent=paused?'Enable motion':'Pause motion';motion.setAttribute('aria-pressed',String(paused));for(const svg of [scene,document.getElementById('scientist-art'),document.getElementById('tesla-scene')]){if(svg?.pauseAnimations){if(paused)svg.pauseAnimations();else svg.unpauseAnimations();}}}
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
const twin=document.getElementById('photon-twin');
let phase='idle', phaseTime=0, frameTime=0;
const centerX=875, centerY=567;
function setPhotonPosition(element,x,y){element?.setAttribute('transform',`translate(${x} ${y})`);}
function animatePhoton(now){
 const elapsed=frameTime?Math.min(now-frameTime,64):0;
 frameTime=now;
 if(photon&&phase!=='idle'&&!paused&&!document.hidden){
  phaseTime+=elapsed;
  const t=phaseTime/1000;
  if(t<1){
   // A one-second tremor immediately after arrival.
   setPhotonPosition(photon,centerX+Math.sin(t*98)*Math.min(4,t*7),centerY+Math.cos(t*111)*3);
  }else if(t<2.9){
   // The split lights turn blue and release a single burst at collision.
   const u=(t-1)/1.9;
   scene.classList.add('blue-phase');
   const distance=38*Math.sin(u*Math.PI);
   const pulse=4*Math.sin(u*Math.PI*2);
   setPhotonPosition(photon,centerX-distance,centerY+pulse);
   setPhotonPosition(twin,centerX+distance,centerY-pulse);
   twin.style.opacity='1';
  }else{
   setPhotonPosition(photon,centerX,centerY);
   twin.style.opacity='0';
   if(phase==='clashing'){
    phase='settled';
    scene.classList.add('plasma-burst','eye-flash');
    replay.textContent='Send another photon ↗';
   }
  }
  if(t>=1&&phase==='vibrating')phase='clashing';
 }
 requestAnimationFrame(animatePhoton);
}
if(photon)requestAnimationFrame(animatePhoton);
function sendPhoton(){
 if(!scene||!flight)return;
 if(paused){replay.textContent='Enable motion to fly';clearTimeout(flightTimer);flightTimer=setTimeout(()=>replay.textContent='Send a photon ↗',2000);return;}
 clearTimeout(flightTimer);phase='idle';phaseTime=0;twin.style.opacity='0';
 scene.classList.remove('plasma-burst','eye-flash','flying','blue-phase');
 void scene.getBoundingClientRect();
 scene.classList.add('flying');
 photon.removeAttribute('transform');flight.beginElement();replay.textContent='Light on its way…';
}
if(replay){replay.addEventListener('click',sendPhoton);if(!paused)setTimeout(sendPhoton,700);}
if('IntersectionObserver' in window&&!media.matches){const observer=new IntersectionObserver(entries=>{entries.forEach(entry=>{if(entry.isIntersecting){entry.target.classList.remove('pending');observer.unobserve(entry.target);}});},{threshold:.08});document.querySelectorAll('.section-head,.archive-links,.timeline li,.interest-grid article').forEach(el=>{el.classList.add('reveal','pending');observer.observe(el);});}
if(flight)flight.addEventListener('endEvent',()=>{
 scene.classList.remove('flying');phase='vibrating';phaseTime=0;frameTime=0;
 setPhotonPosition(photon,centerX,centerY);
});

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

// Mouse, touch and keyboard activation share the same discharge trigger.
const coil=document.getElementById('tesla-scene');
let coilTimer;
function dischargeCoil(){
 if(!coil)return;
 clearTimeout(coilTimer);
 coil.classList.remove('tesla-triggered');
 void coil.getBoundingClientRect();
 coil.classList.add('tesla-triggered');
 coilTimer=setTimeout(()=>coil.classList.remove('tesla-triggered'),1600);
}
document.querySelectorAll('.coil-touch,.spark-coil').forEach(button=>button.addEventListener('click',dischargeCoil));
