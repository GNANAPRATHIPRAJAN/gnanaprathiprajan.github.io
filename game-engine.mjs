// Small deterministic game engine. Rendering and input live in games.js.
export const W=600,H=420;
const clamp=(v,a,b)=>Math.max(a,Math.min(b,v));
export function createGame(type,rng=Math.random){
 const g={type,score:0,over:false,won:false,elapsed:0};
 const end=(win=false)=>{g.over=true;g.won=win;};
 if(type==='snake'){
  g.snake=[{x:10,y:10},{x:9,y:10},{x:8,y:10}];g.dir={x:1,y:0};g.next={...g.dir};g.tick=0;g.turned=false;
  const food=()=>{const free=[];for(let y=0;y<21;y++)for(let x=0;x<30;x++)if(!g.snake.some(s=>s.x===x&&s.y===y))free.push({x,y});if(!free.length){end(true);return;}g.food=free[Math.floor(rng()*free.length)];};food();
  g.input=k=>{const d={left:{x:-1,y:0},right:{x:1,y:0},up:{x:0,y:-1},down:{x:0,y:1}}[k];if(d&&!g.turned&&!(d.x===-g.dir.x&&d.y===-g.dir.y)){g.next=d;g.turned=true;}};
  g.update=dt=>{if(g.over)return;g.tick+=dt;const interval=Math.max(.07,.14-g.score*.001);if(g.tick<interval)return;g.tick-=interval;g.dir=g.next;g.turned=false;const h={x:g.snake[0].x+g.dir.x,y:g.snake[0].y+g.dir.y};const eat=h.x===g.food.x&&h.y===g.food.y;if(h.x<0||h.x>=30||h.y<0||h.y>=21||g.snake.slice(0,eat?undefined:-1).some(s=>s.x===h.x&&s.y===h.y)){end();return;}g.snake.unshift(h);if(eat){g.score+=10;food();}else g.snake.pop();};
 }else if(type==='breaker'){
  g.paddle=250;g.ball={x:300,y:340,vx:150,vy:-215};g.bricks=[];g.lives=3;for(let r=0;r<5;r++)for(let c=0;c<9;c++)g.bricks.push({x:24+c*62,y:35+r*25,w:54,h:16});
  g.input=()=>{};
  g.update=(dt,keys={})=>{if(g.over)return;g.paddle=clamp(g.paddle+((keys.right?1:0)-(keys.left?1:0))*370*dt,0,W-100);const b=g.ball,oldY=b.y;b.x+=b.vx*dt;b.y+=b.vy*dt;if(b.x<7){b.x=7;b.vx=Math.abs(b.vx);}if(b.x>W-7){b.x=W-7;b.vx=-Math.abs(b.vx);}if(b.y<7){b.y=7;b.vy=Math.abs(b.vy);}if(b.vy>0&&oldY+7<=H-30&&b.y+7>=H-30&&b.x>=g.paddle-7&&b.x<=g.paddle+107){b.y=H-38;b.vy=-Math.abs(b.vy);b.vx=((b.x-(g.paddle+50))/50)*250;}
  for(let i=0;i<g.bricks.length;i++){const r=g.bricks[i];if(b.x+7>=r.x&&b.x-7<=r.x+r.w&&b.y+7>=r.y&&b.y-7<=r.y+r.h){g.bricks.splice(i,1);b.vy=-b.vy;g.score+=10;break;}}
  if(!g.bricks.length)end(true);if(b.y>H+10){g.lives--;if(!g.lives)end();else Object.assign(b,{x:g.paddle+50,y:340,vx:150,vy:-215});}};
 }else if(type==='stack'){
  g.blocks=[{x:200,y:H-24,w:200}];g.moving={x:0,y:H-46,w:200};g.speed=140;g.direction=1;
  g.input=k=>{if(k!=='action'||g.over)return;const prev=g.blocks.at(-1),m=g.moving,left=Math.max(prev.x,m.x),right=Math.min(prev.x+prev.w,m.x+m.w);if(right<=left){end();return;}g.blocks.push({x:left,y:m.y,w:right-left});g.score++;if(g.score>=30){end(true);return;}if(m.y<120)g.blocks.forEach(b=>b.y+=22);g.moving={x:0,y:g.blocks.at(-1).y-22,w:right-left};g.speed=Math.min(340,g.speed+8);g.direction=1;};
  g.update=dt=>{if(g.over)return;const m=g.moving;m.x+=g.direction*g.speed*dt;if(m.x+m.w>=W){m.x=W-m.w;g.direction=-1;}if(m.x<=0){m.x=0;g.direction=1;}};
 }else if(type==='dino'){
  g.y=330;g.vy=0;g.obstacles=[];g.spawn=1.2;g.distance=0;
  g.input=k=>{if((k==='action'||k==='up')&&g.y>=330&&!g.over)g.vy=-540;};
  g.update=(dt,keys={})=>{if(g.over)return;g.elapsed+=dt;g.distance+=dt*12;g.score=Math.floor(g.distance);g.vy+=1500*dt;g.y=Math.min(330,g.y+g.vy*dt);if(g.y===330)g.vy=0;g.spawn-=dt;const speed=Math.min(420,240+g.elapsed*3);if(g.spawn<=0){g.obstacles.push({x:W+20,w:20+rng()*15,h:25+rng()*30});g.spawn=1.15+rng()*.75;}for(const o of g.obstacles){o.x-=speed*dt;if(o.x<112&&o.x+o.w>83&&g.y>360-o.h&&g.y-38<360)end();}g.obstacles=g.obstacles.filter(o=>o.x+o.w>0);};
 }else if(type==='catch'){
  g.paddle=250;g.drops=[];g.spawn=.3;g.lives=3;
  g.input=()=>{};
  g.update=(dt,keys={})=>{if(g.over)return;g.elapsed+=dt;g.paddle=clamp(g.paddle+((keys.right?1:0)-(keys.left?1:0))*360*dt,0,W-90);g.spawn-=dt;if(g.spawn<=0){g.drops.push({x:15+rng()*(W-30),y:-10});g.spawn=Math.max(.35,.9-g.elapsed*.004);}for(let i=g.drops.length-1;i>=0;i--){const d=g.drops[i],old=d.y;d.y+=(140+Math.min(160,g.elapsed*2))*dt;if(old<375&&d.y>=375&&d.x>=g.paddle-7&&d.x<=g.paddle+97){g.score+=10;g.drops.splice(i,1);}else if(d.y>H+10){g.drops.splice(i,1);if(--g.lives===0)end();}}};
 }else{
  g.ship=300;g.bullets=[];g.enemies=[];g.direction=1;g.fire=0;g.wave=1;const wave=()=>{g.enemies=[];for(let r=0;r<3;r++)for(let c=0;c<8;c++)g.enemies.push({x:65+c*60,y:45+r*36});};wave();g.input=k=>{if(k==='action'&&g.fire<=0&&!g.over){g.bullets.push({x:g.ship,y:H-45});g.fire=.22;}};
  g.update=(dt,keys={})=>{if(g.over)return;g.fire-=dt;g.ship=clamp(g.ship+((keys.right?1:0)-(keys.left?1:0))*300*dt,15,W-15);if(keys.action)g.input('action');for(const b of g.bullets)b.y-=450*dt;g.bullets=g.bullets.filter(b=>b.y>-10);let edge=false;for(const e of g.enemies){e.x+=g.direction*(26+g.wave*8)*dt;if(e.x<18||e.x>W-18)edge=true;}if(edge){g.direction*=-1;for(const e of g.enemies){e.x=clamp(e.x,18,W-18);e.y+=20;}}
  for(let i=g.bullets.length-1;i>=0;i--){const b=g.bullets[i],j=g.enemies.findIndex(e=>Math.abs(e.x-b.x)<17&&Math.abs(e.y-b.y)<13);if(j>=0){g.enemies.splice(j,1);g.bullets.splice(i,1);g.score+=10;}}
  if(g.enemies.some(e=>e.y>H-65))end();if(!g.enemies.length){if(g.wave===3)end(true);else{g.wave++;wave();}}};
 }
 return g;
}
