const chess=document.getElementById('chess');
const opener=document.getElementById('open-chess');
const back=document.getElementById('close-chess');
const selector=document.querySelector('.game-picker');
const panel=document.querySelector('.game-panel');
const heading=document.querySelector('.games-heading');
function showChess(open){
 chess.hidden=!open;
 selector.hidden=open;panel.hidden=open;heading.hidden=open;opener.hidden=open;
 if(open){const pause=document.getElementById('pause-game');if(!pause.disabled&&pause.textContent==='Pause')pause.click();window.scrollTo({top:0,behavior:'instant'});back.focus({preventScroll:true});}
 else{opener.focus({preventScroll:true});}
}
opener.addEventListener('click',()=>showChess(true));
back.addEventListener('click',()=>showChess(false));
