export const WORDS = ['atom','wave','light','orbit','force','space','time','mass','energy','laser','field','phase','spin','star','moon','earth','cloud','rain','river','green','blue','learn','think','focus','calm','hello','world','code','graph','model','data','train','neuron','plasma','photon','motion','signal','quantum','protein','science'];
export class TypingRain {
  constructor({mode='words', speed='slow', random=Math.random}={}) {
    this.mode=mode; this.speed=speed; this.random=random;
    this.tokens=[]; this.elapsed=0; this.spawnIn=0; this.serial=0;
    this.score=0; this.hits=0; this.misses=0; this.correctChars=0; this.typedChars=0; this.over=false;
  }
  spawn() {
    if(this.tokens.length>=6)return;
    const pool=this.mode==='letters'?'abcdefghijklmnopqrstuvwxyz':WORDS;
    const available=Array.from(pool).filter(word=>!this.tokens.some(t=>t.word===word));
    if(!available.length)return;
    const word=available[Math.min(available.length-1,Math.floor(this.random()*available.length))];
    this.tokens.push({id:++this.serial,word,y:3,lane:(this.serial-1)%3});
  }
  update(dt) {
    if(this.over)return;
    dt=Math.max(0,Math.min(dt,0.1)); this.elapsed=Math.min(60,this.elapsed+dt);
    this.spawnIn-=dt;
    if(this.spawnIn<=0){this.spawn();this.spawnIn=this.speed==='slow'?4:2.8;}
    for(const token of this.tokens)token.y+=dt*(this.speed==='slow'?3.3:5);
    const missed=this.tokens.filter(t=>t.y>=91);
    this.misses+=missed.length; this.tokens=this.tokens.filter(t=>t.y<91);
    this.over=this.elapsed>=60||this.misses>=5;
  }
  type(value, added=0) {
    if(this.over)return null;
    this.typedChars+=Math.max(0,added);
    const word=value.trim().toLowerCase();
    const match=this.tokens.filter(t=>t.word===word).sort((a,b)=>b.y-a.y)[0];
    if(!match)return null;
    this.tokens=this.tokens.filter(t=>t.id!==match.id);
    this.hits++; this.correctChars+=match.word.length; this.score+=match.word.length*10;
    return match;
  }
  get accuracy(){return this.typedChars?Math.min(100,Math.round(this.correctChars/this.typedChars*100)):100;}
  get wpm(){return this.elapsed?Math.round(this.correctChars/5/(this.elapsed/60)):0;}
}
