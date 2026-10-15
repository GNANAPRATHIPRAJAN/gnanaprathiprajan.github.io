import {TypingRain} from './typing-engine.mjs?v=1';
const $=id=>document.getElementById(id);
const section=$('typing'), arena=$('typing-arena'), input=$('typing-input'), start=$('typing-start'), pause=$('typing-pause');
const status=$('typing-status'), overlay=$('typing-overlay'), mode=$('typing-mode'), speed=$('typing-speed');
const homeParts=[document.querySelector('.games-heading'),document.querySelector('.game-picker'),document.querySelector('.game-panel'),$('extra-games')];
let game=new TypingRain(), running=false, paused=false, previous='', last=0, frameId=0;
const nodes=new Map();
function key(){return 'typing-rain-best-'+mode.value+'-'+speed.value;}
function best(){try{return Number(localStorage.getItem(key()))||0;}catch{return 0;}}
function saveBest(){try{localStorage.setItem(key(),String(Math.max(best(),game.score)));}catch{/* Storage may be disabled. */}}
function render(){
  $('typing-score').textContent=game.score; $('typing-best').textContent=Math.max(best(),game.score);
  $('typing-time').textContent=Math.ceil(60-game.elapsed)+'s'; $('typing-lives').textContent=Math.max(0,5-game.misses);
  $('typing-accuracy').textContent=game.accuracy+'%'; $('typing-wpm').textContent=game.wpm;
  const typed=input.value.trim().toLowerCase();
  for(const token of game.tokens){
    let node=nodes.get(token.id);
    if(!node){node=document.createElement('span');node.className='typing-token';node.setAttribute('aria-hidden','true');arena.appendChild(node);nodes.set(token.id,node);}
    node.style.left=((token.lane+0.5)*100/3)+'%'; node.style.top=token.y+'%';
    node.classList.toggle('target',Boolean(typed)&&token.word.startsWith(typed));
    const count=typed&&token.word.startsWith(typed)?typed.length:0;
    const prefix=document.createElement('b');prefix.textContent=token.word.slice(0,count);
    node.replaceChildren(prefix,document.createTextNode(token.word.slice(count)));
  }
  for(const [id,node] of nodes)if(!game.tokens.some(t=>t.id===id)){node.remove();nodes.delete(id);}
  overlay.hidden=running&&!paused&&!game.over;
  overlay.textContent=game.over?'Round complete · '+game.score+' points':paused?'Paused · take your time':'Ready for a little word rain?';
}
function announceTargets(){
  $('typing-targets').textContent='On screen: '+game.tokens.map(t=>t.word).join(', ')+'.';
}
function frame(now){
  if(!running||paused||section.hidden||game.over){frameId=0;return;}
  const dt=last?(now-last)/1000:0;last=now;
  const previousMisses=game.misses, previousCount=game.serial;
  game.update(dt);
  if(previousCount!==game.serial)announceTargets();
  if(game.misses>previousMisses){status.textContent='Missed a word. '+Math.max(0,5-game.misses)+' lives remaining.';announceTargets();}
  render();
  if(game.over){running=false;saveBest();input.disabled=true;pause.disabled=true;mode.disabled=speed.disabled=false;status.textContent='Round complete! '+game.score+' points, '+game.hits+' correct, '+game.accuracy+'% accuracy. Press Play again to retry.';start.textContent='Play again';return;}
  frameId=requestAnimationFrame(frame);
}
function reset(){
  cancelAnimationFrame(frameId);frameId=0;
  game=new TypingRain({mode:mode.value,speed:speed.value});running=false;paused=false;last=0;previous='';input.value='';input.disabled=true;
  pause.disabled=true;pause.textContent='Pause';mode.disabled=speed.disabled=false;start.textContent='Start rain';
  status.textContent='Type a complete falling letter or word. Correct answers disappear automatically. Each character earns 10 points.';
  $('typing-targets').textContent='';render();
}
function setPaused(value){
  if(!running||game.over||paused===value)return;
  paused=value;input.disabled=value;pause.textContent=value?'Resume':'Pause';
  status.textContent=value?'Paused. Resume when you are ready.':'Keep going! Type any falling word.';
  if(value){cancelAnimationFrame(frameId);frameId=0;}else{last=0;input.focus({preventScroll:true});frameId=requestAnimationFrame(frame);}
  render();
}
$('open-typing').addEventListener('click',()=>{
  const otherPause=$('pause-game');if(!otherPause.disabled&&otherPause.textContent==='Pause')otherPause.click();
  homeParts.forEach(el=>el.hidden=true);$('chess').hidden=true;section.hidden=false;
  window.scrollTo({top:0,behavior:'instant'});$('typing-heading').focus({preventScroll:true});render();
});
$('close-typing').addEventListener('click',()=>{setPaused(true);section.hidden=true;homeParts.forEach(el=>el.hidden=false);$('open-typing').focus({preventScroll:true});});
start.addEventListener('click',()=>{
  reset();running=true;input.disabled=false;pause.disabled=false;mode.disabled=speed.disabled=true;
  start.textContent='Restart';status.textContent='Go! Type any falling word before it reaches the floor.';
  game.spawn();game.spawnIn=speed.value==='slow'?4:2.8;announceTargets();render();input.focus({preventScroll:true});frameId=requestAnimationFrame(frame);
});
pause.addEventListener('click',()=>setPaused(!paused));
input.addEventListener('input',event=>{
  if(!running||paused)return;
  if(event.isComposing)return;
  const value=input.value.replace(/[^a-zA-Z]/g,'');input.value=value;
  const added=Math.max(0,value.length-previous.length); const match=game.type(value,added);previous=value;
  if(match){input.value='';previous='';status.textContent='Correct: '+match.word+'! +'+match.word.length*10+' points.';saveBest();announceTargets();}
  render();
});
input.addEventListener('keydown',event=>{if(event.key==='Escape'){event.preventDefault();setPaused(true);pause.focus();}if(event.key==='Enter')event.preventDefault();});
mode.addEventListener('change',reset);speed.addEventListener('change',reset);
window.addEventListener('blur',()=>setPaused(true));document.addEventListener('visibilitychange',()=>{if(document.hidden)setPaused(true);});
$('motion').addEventListener('click',()=>{if(document.documentElement.classList.contains('paused'))setPaused(true);});
reset();
