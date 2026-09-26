import {chooseBotMove} from './chess-bot.mjs';
self.onmessage=({data})=>{try{self.postMessage({move:chooseBotMove(data.fen,data.level)});}catch{self.postMessage({error:true});}};
