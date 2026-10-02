// A conceptual gene-expression illustration, independent of the research data.
// Biology references: https://www.genome.gov/genetics-glossary/Messenger-RNA-mRNA
// and https://www.genome.gov/genetics-glossary/Transfer-RNA-tRNA
(() => {
  'use strict';
  const art = document.getElementById('phd-molecule');
  if (!art) return;
  const find = name => art.querySelector(`[data-molecule-${name}]`);
  const primary = find('primary'), secondary = find('secondary'), shadow = find('shadow');
  const frame = find('frame'), transfer = find('transfer'), ribosome = find('ribosome');
  const state = find('state'), process = find('process');
  const rungs = [...art.querySelectorAll('[data-molecule-rung]')];
  const beads = [...art.querySelectorAll('[data-molecule-bead]')];
  const steps = [...art.querySelectorAll('[data-molecule-step]')];
  const count = 81, duration = 14200;
  let elapsed = 0, previous = null, raf = null, visible = true;
  let lastLabel = '';
  const clamp = x => Math.max(0, Math.min(1, x));
  const smooth = x => { const t = clamp(x); return t * t * (3 - 2 * t); };
  const mix = (a, b, t) => a.map((v, i) => v + (b[i] - v) * t);
  const dna = (u, phase, side = 1) => [32 + 276 * u, 105 + side * 43 * Math.sin(5 * Math.PI * u + phase)];
  const rna = u => [32 + 276 * u, 108 + 18 * Math.sin(3 * Math.PI * u + .3)];
  const protein = u => [170 + 70 * Math.sin(2 * Math.PI * u + .5) + 27 * Math.sin(6 * Math.PI * u), 105 + 38 * Math.cos(4 * Math.PI * u + .3) + 12 * Math.sin(10 * Math.PI * u)];
  const path = points => points.map((p, i) => `${i ? 'L' : 'M'}${p[0].toFixed(2)} ${p[1].toFixed(2)}`).join(' ');
  function render(time) {
    const t = (time % duration) / 1000;
    const transcription = smooth((t - 2.7) / 1.9);
    const translation = smooth((t - 6.8) / 2.4);
    const phase = .22 * Math.sin(t * 1.15) * (1 - transcription);
    const point = u => mix(mix(dna(u, phase), rna(u), transcription), protein(u), translation);
    const points = Array.from({length: count}, (_, i) => point(i / (count - 1)));
    primary.setAttribute('d', path(points));
    shadow.setAttribute('d', path(points));
    secondary.setAttribute('d', path(Array.from({length: count}, (_, i) => mix(dna(i / (count - 1), phase, -1), rna(i / (count - 1)), transcription))));
    secondary.setAttribute('opacity', String(1 - transcription));
    rungs.forEach(line => {
      const u = Number(line.dataset.moleculeRung);
      const a = point(u), b = mix(dna(u, phase, -1), [rna(u)[0], rna(u)[1] - 8], transcription);
      line.setAttribute('x1', a[0].toFixed(2)); line.setAttribute('y1', a[1].toFixed(2));
      line.setAttribute('x2', b[0].toFixed(2)); line.setAttribute('y2', b[1].toFixed(2));
      line.setAttribute('opacity', String(1 - translation));
    });
    beads.forEach(circle => {
      const p = points[Number(circle.dataset.moleculeBead)];
      circle.setAttribute('cx', p[0].toFixed(2)); circle.setAttribute('cy', p[1].toFixed(2));
      circle.setAttribute('r', (2.6 + translation * 1.5).toFixed(2));
    });
    const adaptor = smooth((t - 4.6) / .8) * (1 - smooth((t - 7.6) / 1.1));
    transfer.setAttribute('opacity', String(adaptor));
    transfer.setAttribute('transform', `translate(${(235 + 3 * Math.sin(t * 2)).toFixed(2)} ${(53 + 3 * Math.cos(t * 2)).toFixed(2)})`);
    ribosome.setAttribute('opacity', String(adaptor * .8));
    // Fade before restarting; the protein never morphs back into DNA.
    frame.setAttribute('opacity', String(t < .45 ? smooth(t / .45) : 1 - smooth((t - 12.8) / .8)));
    const step = t < 2.7 ? 0 : t < 6.8 ? 1 : 2;
    const label = t < 2.7 ? 'DNA' : t < 5.2 ? 'mRNA' : t < 6.8 ? 'mRNA + tRNA' : t < 9.2 ? 'Protein chain' : 'Folded protein';
    if (label !== lastLabel) {
      state.textContent = label;
      process.textContent = t < 2.7 ? 'Genetic information' : t < 5.2 ? 'Transcription' : t < 9.2 ? 'Translation' : 'Protein folding';
      steps.forEach((node, i) => node.classList.toggle('is-active', i === step));
      lastLabel = label;
    }
  }
  function running() { return visible && !document.hidden && !document.documentElement.classList.contains('paused'); }
  function tick(now) {
    raf = null;
    if (!running()) { previous = null; return; }
    if (previous === null) previous = now;
    const delta = now - previous;
    if (delta >= 32) {
      elapsed += Math.min(delta, 80);
      previous = now;
      render(elapsed);
    }
    raf = requestAnimationFrame(tick);
  }
  function sync() {
    if (running() && raf === null) { previous = null; raf = requestAnimationFrame(tick); }
    else if (!running() && raf !== null) { cancelAnimationFrame(raf); raf = null; previous = null; }
  }
  // Keep the server-rendered DNA as a readable still when motion is disabled.
  new MutationObserver(sync).observe(document.documentElement, {attributes: true, attributeFilter: ['class']});
  document.addEventListener('visibilitychange', sync);
  if ('IntersectionObserver' in window) new IntersectionObserver(entries => {
    visible = entries[0].isIntersecting;
    sync();
  }, {threshold: 0}).observe(art);
  sync();
})();
