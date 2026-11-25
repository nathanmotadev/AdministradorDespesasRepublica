// Roda antes do CSS pintar a tela, para não piscar o tema errado ao abrir o app.
// A escolha fica no navegador: "claro", "escuro" ou "auto" (segue o sistema).
(function () {
    var preferencia = 'auto';
    try { preferencia = localStorage.getItem('republica.tema') || 'auto'; } catch (e) { /* sem armazenamento */ }
    var escuro = preferencia === 'escuro'
        || (preferencia === 'auto' && window.matchMedia && matchMedia('(prefers-color-scheme: dark)').matches);
    document.documentElement.dataset.tema = escuro ? 'escuro' : 'claro';
}());
