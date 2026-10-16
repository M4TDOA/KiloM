/* KiloM motion: short, interruptible transitions. No delay before interaction. */
(function () {
  'use strict';
  const reduced = window.matchMedia ? window.matchMedia('(prefers-reduced-motion: reduce)') : {matches:false};
  const active = new Set();
  function animate(element, frames, options) {
    if (!element || reduced.matches || typeof element.animate !== 'function') return;
    const animation = element.animate(frames, options);
    active.add(animation);
    animation.onfinish = animation.oncancel = () => active.delete(animation);
  }
  function cancel() { for (const animation of active) animation.cancel(); active.clear(); }
  reduced.addEventListener?.('change', event => { if(event.matches) cancel(); });
  window.KiloMotion = {
    enter(direction = 0) {
      cancel();
      if (reduced.matches) return;
      let count = 0;
      for (const element of document.querySelectorAll('#app .intro, #app .card')) {
        const rect = element.getBoundingClientRect();
        if (rect.bottom <= 0 || rect.top >= window.innerHeight || count >= 6) continue;
        animate(element, [
          {opacity:0, transform:`translate3d(${direction * 8}px, 8px, 0)`},
          {opacity:1, transform:'translate3d(0, 0, 0)'}
        ], {duration:200, delay:count++ * 16, easing:'cubic-bezier(.2, 0, 0, 1)', fill:'backwards'});
      }
    },
    stop(element) {
      animate(element, [{opacity:0, transform:'translate3d(0, 8px, 0)'}, {opacity:1, transform:'translate3d(0, 0, 0)'}], {duration:200, easing:'cubic-bezier(.2, 0, 0, 1)'});
    }
  };
})();
