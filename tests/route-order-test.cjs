const assert=require('node:assert/strict');
const {optimize,reorder}=require('../app/src/main/assets/route-order.js');
const data=require('../app/src/main/assets/distances.json');
const distance=(r,m)=>r.slice(1).reduce((v,b,i)=>v+Math.round(m[r[i]][b]*1000),0);
const perms=a=>a.length<2?[a]:a.flatMap((x,i)=>perms(a.filter((_,j)=>i!==j)).map(p=>[x,...p]));
let checks=0;function ok(v,msg){assert(v,msg);checks++;}
// Exhaustive independent oracle, including directed and zero-distance edges.
let seed=729;const random=()=>{seed=(Math.imul(seed,1664525)+1013904223)>>>0;return seed/2**32;};
for(let test=0;test<12;test++){
 const n=3+test%5,m=Array.from({length:n},(_,a)=>Array.from({length:n},(_,b)=>a===b?0:Math.floor(random()*300)/10));
 for(const closed of [false,true]){
  const stops=Array.from({length:n-1},(_,i)=>i+1),r=[0,...stops,...(closed?[0]:[])];
  const best=Math.min(...perms(stops).map(p=>distance([0,...p,...(closed?[0]:[])],m)));
  const out=optimize(r,m);ok(distance(out.route,m)===best,'Exact shortest directed route');
  ok(out.route[0]===0&&(!closed||out.route.at(-1)===0),'Endpoints');
 }
}
for(const closed of [false,true]){
 const r=[data.admin,8,42,12,23,6,44,...(closed?[data.admin]:[])];
 const out=optimize(r,data.matrix);ok(out.after<=out.before,'Actual CSSPO grid');
 const long=[data.admin,...Array.from({length:28},(_,i)=>i+2).reverse(),...(closed?[data.admin]:[])];
 const large=optimize(long,data.matrix);ok(!large.exact&&large.after<=large.before,'Long route never worsens');
 ok([...large.route].sort((a,b)=>a-b).join() === [...long].sort((a,b)=>a-b).join(),'Every requested stop retained');
}
const repeated=[1,6,44,6,1],out=optimize(repeated,data.matrix);
ok([...out.route].sort().join()===[...repeated].sort().join(),'Repeated schools retained');
ok(out.route.every((n,i)=>!i||n!==out.route[i-1]),'No consecutive duplicates');
ok(optimize([1,44,1],data.matrix).route.join()==='1,44,1','Single school roundtrip unchanged');
ok(reorder([1,6,44,1],1,2).join()==='1,44,6,1','Manual ordering');
ok(reorder([1,6,44,1],3,1)===null,'Fixed return');
ok(reorder([1,6,44,1],0,1)===null,'Fixed departure');
ok(reorder([1,6,44,6,1],3,2)===null,'Adjacent duplicate rejected');
console.log(`PASS: ${checks} route optimization checks`);
