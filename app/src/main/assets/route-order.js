/* Directed distances; no GPS, network requests or personal information. */
(function(root){
'use strict';
const edge=(matrix,a,b)=>a===b?Infinity:Math.round(matrix[a][b]*1000);
const cost=(route,matrix)=>route.slice(1).reduce((sum,b,i)=>sum+edge(matrix,route[i],b),0);
function optimize(route,matrix){
 const original=[...route],closed=route.length>2&&route[0]===route.at(-1);
 const start=route[0],stops=route.slice(1,closed?-1:undefined),n=stops.length;
 const pack=order=>[start,...order,...(closed?[start]:[])];
 const before=cost(original,matrix);
 if(n<2)return {route:original,before:before/1000,after:before/1000,exact:true};
 let candidate;
 if(n<=14){
  // Held–Karp supports asymmetric distances and an optional fixed return.
  const size=1<<n,dp=new Float64Array(size*n),parent=new Int8Array(size*n);
  dp.fill(Infinity);parent.fill(-1);
  for(let j=0;j<n;j++)dp[(1<<j)*n+j]=edge(matrix,start,stops[j]);
  for(let mask=1;mask<size;mask++)for(let j=0;j<n;j++){
   const value=dp[mask*n+j];if(!Number.isFinite(value))continue;
   for(let k=0;k<n;k++)if(!(mask&(1<<k))){
    const at=(mask|(1<<k))*n+k,next=value+edge(matrix,stops[j],stops[k]);
    if(next<dp[at]){dp[at]=next;parent[at]=j;}
   }
  }
  let best=Infinity,last=-1;
  for(let j=0;j<n;j++){const value=dp[(size-1)*n+j]+(closed?edge(matrix,stops[j],start):0);if(value<best){best=value;last=j;}}
  if(last>=0){let mask=size-1,order=[];while(last>=0){order.push(stops[last]);const prev=parent[mask*n+last];mask^=1<<last;last=prev;}candidate=pack(order.reverse());}
 }else{
  // Larger routes: multistart nearest neighbour, then directed relocation/reversal.
  let bestOrder=[...stops],best=before;
  for(let first=0;first<n;first++){
   let remaining=stops.map((_,i)=>i).filter(i=>i!==first),order=[stops[first]],last=stops[first];
   while(remaining.length){let pos=0;for(let i=1;i<remaining.length;i++)if(edge(matrix,last,stops[remaining[i]])<edge(matrix,last,stops[remaining[pos]]))pos=i;last=stops[remaining.splice(pos,1)[0]];order.push(last);}
   const value=cost(pack(order),matrix);if(value<best){best=value;bestOrder=order;}
  }
  for(let pass=0;pass<20;pass++){
   let improved=null;
   for(let i=0;i<n;i++)for(let j=0;j<n;j++)if(i!==j){
    let order=[...bestOrder],item=order.splice(i,1)[0];order.splice(j,0,item);
    let value=cost(pack(order),matrix);if(value<best){best=value;improved=order;}
    if(i<j){order=[...bestOrder.slice(0,i),...bestOrder.slice(i,j+1).reverse(),...bestOrder.slice(j+1)];value=cost(pack(order),matrix);if(value<best){best=value;improved=order;}}
   }
   if(!improved)break;bestOrder=improved;
  }
  candidate=pack(bestOrder);
 }
 const after=candidate?cost(candidate,matrix):Infinity;
 // Keep the user's order on ties, invalid candidates or a longer route.
 return {route:after<before?candidate:original,before:before/1000,after:Math.min(before,after)/1000,exact:n<=14};
}
function movableEnd(route){return route.length-(route.length>2&&route[0]===route.at(-1)?2:1);}
function reorder(route,from,to){
 if(!Number.isInteger(from)||!Number.isInteger(to)||from<1||to<1||from>movableEnd(route)||to>movableEnd(route))return null;
 const copy=[...route],item=copy.splice(from,1)[0];copy.splice(to,0,item);
 return copy.some((n,i)=>i&&n===copy[i-1])?null:copy;
}
function bind(document,callbacks){
 let drag=null,frame=0;const win=document.defaultView;
 function mark(){
  if(!drag)return;
  const rows=[...document.querySelectorAll('#routeList [data-stop-index]')].filter(row=>row.querySelector('[data-drag-stop]:not(:disabled)'));
  let target=drag.from,nearest=Infinity;
  for(const row of rows){const box=row.getBoundingClientRect(),d=Math.abs(drag.y-(box.top+box.height/2));if(d<nearest){nearest=d;target=Number(row.dataset.stopIndex);}}
  drag.to=target;
  for(const row of rows)row.classList.toggle('drop-target',Number(row.dataset.stopIndex)===target&&target!==drag.from);
 }
 function tick(){if(!drag)return;if(!drag.handle.isConnected){finish(false);return;}const h=win.innerHeight,dy=drag.y<100?-10:drag.y>h-115?10:0;if(dy)win.scrollBy(0,dy);mark();frame=win.requestAnimationFrame(tick);}
 function finish(apply){
  if(!drag)return;const current=drag;drag=null;win.cancelAnimationFrame(frame);
  try{current.handle.releasePointerCapture(current.id);}catch(_){}
  document.querySelectorAll('.dragging,.drop-target').forEach(node=>node.classList.remove('dragging','drop-target'));
  if(apply&&current.from!==current.to)callbacks.move(current.from,current.to);
 }
 document.addEventListener('pointerdown',event=>{
  const handle=event.target.closest('[data-drag-stop]');if(!handle||handle.disabled||event.button!==0||drag)return;
  event.preventDefault();handle.focus({preventScroll:true});
  drag={handle,id:event.pointerId,from:Number(handle.dataset.dragStop),to:Number(handle.dataset.dragStop),y:event.clientY};
  handle.closest('.stop').classList.add('dragging');try{handle.setPointerCapture(event.pointerId);}catch(_){}
  frame=win.requestAnimationFrame(tick);
 });
 document.addEventListener('pointermove',event=>{if(drag&&event.pointerId===drag.id){event.preventDefault();drag.y=event.clientY;mark();}},{passive:false});
 document.addEventListener('pointerup',event=>{if(drag&&event.pointerId===drag.id)finish(true);});
 document.addEventListener('pointercancel',event=>{if(drag&&event.pointerId===drag.id)finish(false);});
 document.addEventListener('lostpointercapture',event=>{if(drag&&event.pointerId===drag.id)finish(false);});
 win.addEventListener('blur',()=>finish(false));
 document.addEventListener('keydown',event=>{
  if(event.key==='Escape'){finish(false);return;}
  const handle=event.target.closest('[data-drag-stop]');if(!handle||handle.disabled)return;
  const from=Number(handle.dataset.dragStop),end=callbacks.end();
  const to=event.key==='ArrowUp'?from-1:event.key==='ArrowDown'?from+1:event.key==='Home'?1:event.key==='End'?end:null;
  if(to!==null){event.preventDefault();callbacks.move(from,to);}
 });
 return ()=>finish(false);
}
const api={optimize,reorder,movableEnd,bind};if(typeof module==='object'&&module.exports)module.exports=api;else root.KiloRouteOrder=api;
})(typeof window==='undefined'?globalThis:window);
