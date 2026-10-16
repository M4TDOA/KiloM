/* Runs in the head so the saved palette is selected before the app is painted. */
(function(){
 'use strict';
 const modes=['light','dark','pink','oled'];
 let current='light';
 function apply(mode){
  current=modes.includes(mode)?mode:'light';
  document.documentElement.dataset.theme=current;
  document.documentElement.style.colorScheme=current==='dark'||current==='oled'?'dark':'light';
  document.querySelector('meta[name="theme-color"]')?.setAttribute('content',({light:'#f9f9ff',dark:'#111318',pink:'#fff8fa',oled:'#000000'})[current]);
 }
 function load(){try{apply(KiloNative.getTheme());}catch(_){apply('light');}}
 function choose(mode){
  if(!modes.includes(mode))return false;
  try{const error=KiloNative.setTheme(mode);if(error){window.toast?.(error);return false;}apply(mode);return true;}
  catch(_){window.toast?.('Impossible d’enregistrer le thème.');return false;}
 }
 window.KiloTheme={get:()=>current,choose,load};load();
})();
