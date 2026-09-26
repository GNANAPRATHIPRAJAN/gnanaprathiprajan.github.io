import {Chess} from './vendor/chess.mjs';
const values={p:100,n:320,b:335,r:500,q:900,k:0};
function evaluate(game){
 let score=0;
 for(const row of game.board())for(const p of row){if(!p)continue;const f=p.square.charCodeAt(0)-97,r=Number(p.square[1])-1,advance=p.color==='w'?r:7-r,center=7-Math.abs(f-3.5)-Math.abs(r-3.5);
 const positional=p.type==='p'?advance*8:('nb'.includes(p.type)?center*9:p.type==='q'?center*3:0);
 score+=(p.color==='w'?1:-1)*(values[p.type]+positional);
 }
 return score*(game.turn()==='w'?1:-1);
}
function ordered(game){return game.moves({verbose:true}).sort((a,b)=>rank(b)-rank(a));}
function rank(m){return (m.captured?10*values[m.captured]-values[m.piece]:0)+(m.promotion?values[m.promotion]:0)+(m.san.includes('+')?40:0);}
export function chooseBotMove(fen,level='medium'){
 const game=new Chess(fen),root=ordered(game);if(!root.length||game.isGameOver())return null;
 const deadline=Date.now()+(level==='hard'?2600:500),maxDepth=level==='hard'?4:2;
 let best=root[0],nodes=0,completedDepth=0;
 function search(depth,alpha,beta,ply){
  if(Date.now()>deadline)throw new Error('time');nodes++;
  if(game.isCheckmate())return -100000+ply;
  if(game.isDraw())return 0;
  if(depth===0)return evaluate(game);
  let score=-Infinity;
  for(const m of ordered(game)){
   game.move(m);let v;
   try{v=-search(depth-1,-beta,-alpha,ply+1);}finally{game.undo();}
   score=Math.max(score,v);alpha=Math.max(alpha,v);if(alpha>=beta)break;
  }
  return score;
 }
 for(let depth=1;depth<=maxDepth;depth++){
  let candidate=best,score=-Infinity,alpha=-Infinity;
  try{
   const moves=[best,...root.filter(m=>m.lan!==best.lan)];
   for(const m of moves){game.move(m);let v;try{v=-search(depth-1,-Infinity,-alpha,1);}finally{game.undo();}if(v>score){score=v;candidate=m;}alpha=Math.max(alpha,v);}
   best=candidate;completedDepth=depth;
  }catch(e){if(e.message!=='time')throw e;break;}
 }
 return {from:best.from,to:best.to,promotion:best.promotion,completedDepth,nodes};
}
