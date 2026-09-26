import {Chess} from './vendor/chess.mjs';
const game=new Chess(),board=document.getElementById('chess-board'),status=document.getElementById('chess-status');
const glyph={wk:'♔',wq:'♕',wr:'♖',wb:'♗',wn:'♘',wp:'♙',bk:'♚',bq:'♛',br:'♜',bb:'♝',bn:'♞',bp:'♟'};
const names={k:'king',q:'queen',r:'rook',b:'bishop',n:'knight',p:'pawn'};
let selected=null, flipped=false, mode='medium', thinking=false, worker=null;
function cancelRobot(){worker?.terminate();worker=null;thinking=false;}
function robotTurn(){
 if(mode==='two'||game.turn()!=='b'||game.isGameOver())return;
 thinking=true;render();
 worker=new Worker('./chess-worker.js?v=11',{type:'module'});
 worker.onmessage=({data})=>{
  cancelRobot();
  if(data.error||!data.move){status.textContent='Robot could not move. Use Undo or start a new game.';return;}
  game.move(data.move);selected=null;render();
 };
 worker.onerror=()=>{cancelRobot();status.textContent='Robot could not load. Try a new game or Two players.';};
 worker.postMessage({fen:game.fen(),level:mode});
}
function render(){
 const targets=selected?game.moves({square:selected,verbose:true}).map(m=>m.to):[];
 board.replaceChildren();
 const files=flipped?'hgfedcba':'abcdefgh',ranks=flipped?'12345678':'87654321';
 for(const rank of ranks)for(const file of files){
  const square=file+rank,piece=game.get(square),btn=document.createElement('button');
  btn.type='button';btn.dataset.square=square;btn.className='chess-square '+((file.charCodeAt(0)+Number(rank))%2?'light':'dark');
  btn.classList.toggle('selected',square===selected);btn.classList.toggle('legal',targets.includes(square));
  btn.setAttribute('aria-label',`${square}${piece?', '+(piece.color==='w'?'White ':'Black ')+names[piece.type]:', empty'}${targets.includes(square)?', legal move':''}`);
  btn.setAttribute('aria-pressed',String(square===selected));
  btn.innerHTML=`<span aria-hidden="true" class="piece ${piece?.color||''}">${piece?glyph[piece.color+piece.type]:''}</span><small aria-hidden="true">${square}</small>`;
  btn.addEventListener('click',()=>choose(square));board.append(btn);
 }
 const side=game.turn()==='w'?'White':'Black';
 status.textContent=game.isCheckmate()?`Checkmate. ${side==='White'?'Black':'White'} wins.`:game.isStalemate()?'Draw by stalemate.':game.isThreefoldRepetition()?'Draw by repetition.':game.isInsufficientMaterial()?'Draw: insufficient material.':game.isDraw()?'Draw.':`${side} to move${game.isCheck()?' — check':''}.${selected?' Choose a highlighted square.':''}`;
 if(thinking)status.textContent='Robot is thinking…';
 document.getElementById('chess-moves').textContent=game.history().map((m,i)=>(i%2===0?`${Math.floor(i/2)+1}. `:'')+m).join(' ')||'No moves yet.';
 document.getElementById('chess-undo').disabled=!game.history().length;
}
function choose(square){
 if(game.isGameOver()||thinking||(mode!=='two'&&game.turn()==='b'))return;
 if(selected){
  const possible=game.moves({square:selected,verbose:true}).filter(m=>m.to===square);
  if(possible.length){game.move({from:selected,to:square,promotion:document.getElementById('chess-promotion').value});selected=null;render();robotTurn();board.querySelector(`[data-square="${square}"]`).focus();return;}
 }
 selected=game.get(square)?.color===game.turn()&&selected!==square?square:null;render();board.querySelector(`[data-square="${square}"]`).focus();
}
document.getElementById('chess-reset').onclick=()=>{cancelRobot();game.reset();selected=null;render();};
document.getElementById('chess-undo').onclick=()=>{cancelRobot();game.undo();if(mode!=='two'&&game.turn()==='b')game.undo();selected=null;render();};
document.getElementById('chess-flip').onclick=()=>{flipped=!flipped;render();};
render();

document.querySelectorAll('[data-chess-mode]').forEach(button=>button.addEventListener('click',()=>{
 cancelRobot();mode=button.dataset.chessMode;game.reset();selected=null;
 document.querySelectorAll('[data-chess-mode]').forEach(b=>b.setAttribute('aria-pressed',String(b===button)));
 document.getElementById('chess-mode-note').textContent=mode==='two'?'Two players on this device. White moves first.':`You play White. The robot plays Black at ${mode} difficulty.`;
 render();
}));
