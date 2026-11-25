import { api } from '../api.js';
import { abrirModal, confirmar, h, toast } from '../ui.js';
import { atualizarAvisos } from '../avisos.js';
import { avatar, badgeDivida, centavos as centavosDe, executar, icone, iconeDaConta, imagemMoradia }
    from '../components.js';
import { brl, dataBR, hojeISO, mesAtual, rotuloMes } from '../format.js';
import { crescer } from '../movimento.js';
import { abrirCobranca } from './cobranca.js';
import { abrirInformarValor } from './contas-fixas.js';
import { moradoresAtivos, nomeDoGrupo } from './moradores.js';

/** "Minha visão": só o que envolve quem está logado. Tudo vem das rotas /api/eu. */
export function viewInicio(ctx) {
    const { state } = ctx;
    const { resumo, dividas, eu } = state;
    const quite = resumo.acertos.length === 0 && dividas.euDevo.length === 0 && dividas.meDevem.length === 0;

    return h('div', { class: 'inicio-grade' },
        h('h1', { class: 'sr-only' }, `Início, ${eu.nome}`),
        cartaoSaldo(ctx),
        resumoContasFixas(ctx),
        proximasContas(ctx),
        h('div', { class: 'stack inicio-resolver' },
            quite
                ? h('section', { class: 'secao quite-secao' },
                    h('div', { class: 'secao-titulo' }, h('h2', {}, 'Pra resolver agora')),
                    h('div', { class: 'card tudo-quite' },
                        h('strong', {}, 'Tudo quite por aqui'),
                        h('span', { class: 'muted' }, 'Nada a pagar nem a receber agora.')))
                : [praResolver(ctx), h('details', { class: 'dividas-detalhe', open: state.dividasAbertas,
                    // a tela é redesenhada a cada escolha: lembra se a pessoa deixou aberto
                    ontoggle: (e) => { state.dividasAbertas = e.target.open; } },
                    h('summary', {}, 'Ver o detalhe de cada dívida'), secaoDividas(ctx))]));
}

/** No computador as contas fixas têm aba própria; no celular ficam dentro da aba Casa. */
const irParaContasFixas = (irPara) => (window.matchMedia('(min-width: 1000px)').matches
    ? irPara('contas')
    : irPara('casa', '.contas-fixas-cartao'));

/** O saldo (o que tenho a receber menos o que devo), com a casinha (ou o prédio) no canto. */
function cartaoSaldo({ state, reverAbertura }) {
    const { resumo, casa } = state;
    const saldo = (centavosDe(resumo.aReceber) - centavosDe(resumo.deve)) / 100;
    const sinal = saldo > 0 ? '+' : saldo < 0 ? '−' : '';
    const [reais, centavos] = brl(Math.abs(saldo)).replace('R$', '').trim().split(',');
    const recado = resumo.deve === 0 && resumo.aReceber === 0 ? 'Nada pendente com você agora.'
        : saldo > 0 ? `Você mais recebe do que deve.${resumo.deve > 0 ? ' Falta pouco pra zerar.' : ''}`
            : saldo < 0 ? `Você mais deve do que recebe.${resumo.aReceber > 0 ? ' Falta pouco pra zerar.' : ''}`
                : 'O que você deve e o que tem a receber se equilibram.';

    return h('section', { class: 'card saldo', 'aria-labelledby': 'titulo-saldo' },
        h('button', { class: 'saldo-casa', type: 'button', 'aria-label': 'Ver a abertura de novo', onclick: reverAbertura },
            imagemMoradia(casa.tipo, '', true)),
        h('p', { id: 'titulo-saldo', class: 'saldo-titulo' }, 'Seu saldo na casa'),
        h('p', { class: `saldo-valor num${saldo < 0 ? ' negativo' : ''}` },
            `${sinal}R$ `, h('strong', {}, `${reais},${centavos}`)),
        h('p', { class: 'saldo-recado' }, recado),
        h('div', { class: 'saldo-grade' },
            h('div', { class: 'saldo-bloco receber' },
                h('span', { class: 'rotulo' }, 'A receber'),
                h('strong', { class: 'num' }, brl(resumo.aReceber))),
            h('div', { class: 'saldo-bloco deve' },
                h('span', { class: 'rotulo' }, 'Você deve'),
                h('strong', { class: 'num' }, brl(resumo.deve)))));
}

/** "R$ 900" quando não há centavos (como no desenho); com centavos, o valor completo. */
const brlCurto = (valor) => (Number.isInteger(valor) ? `R$ ${valor.toLocaleString('pt-BR')}` : brl(valor));

/**
 * Resumo do mês só com as contas fixas: quanto eu pago por mês em cada uma (aluguel, luz...).
 * É só a minha parte (e de quem paga junto comigo); o que os outros moradores pagam não aparece.
 */
const ROTULO_SITUACAO = { QUITADA: '✓ pago', EM_ABERTO: 'em aberto', A_VENCER: 'a vencer' };

function resumoContasFixas({ state, irPara }) {
    const casaId = state.casa.id;
    const MAX_ITENS = 4;
    const titulo = h('h2', { id: 'titulo-resumo-fixas' }, `Resumo de ${rotuloMes(mesAtual()).split(' ')[0].toLowerCase()}`);
    const total = h('p', { class: 'resumo-fixas-total num' });
    const pilula = h('span', { class: 'resumo-fixas-pilula' });
    const subtitulo = h('p', { class: 'resumo-fixas-sub' }, 'suas contas fixas por mês');
    const barra = h('div', { class: 'resumo-barra', role: 'img' });
    const legenda = h('div', { class: 'resumo-fixas-legenda' });
    const cartao = h('section', { class: 'card resumo-fixas', 'aria-labelledby': 'titulo-resumo-fixas', hidden: true },
        h('div', { class: 'secao-titulo' }, titulo,
            h('button', { class: 'link', type: 'button', onclick: () => irParaContasFixas(irPara) }, 'Ver detalhes')),
        h('div', { class: 'resumo-fixas-linha' }, total, pilula),
        subtitulo,
        barra, legenda);

    function mostrar(minha) {
        if (minha.itens.length === 0) { cartao.hidden = true; return; }
        const itens = minha.itens.slice(0, MAX_ITENS);
        const resto = minha.itens.slice(MAX_ITENS).reduce((soma, i) => soma + centavosDe(i.valor), 0);
        const restantes = minha.itens.slice(MAX_ITENS);
        if (resto > 0) {
            itens.push({ rotulo: 'Outras', valor: resto / 100,
                situacao: restantes.every((i) => i.situacao === 'QUITADA') ? 'QUITADA'
                    : restantes.some((i) => i.situacao === 'EM_ABERTO') ? 'EM_ABERTO' : 'A_VENCER' });
        }
        const [inteiro, cents] = brl(minha.total).replace('R$', '').trim().split(',');
        total.replaceChildren('R$ ', h('strong', {}, `${inteiro},${cents}`));
        const quitadas = minha.itens.filter((i) => i.situacao === 'QUITADA').length;
        const lancadas = minha.itens.filter((i) => i.situacao !== 'A_VENCER').length;
        const todas = minha.itens.length;
        pilula.className = `resumo-fixas-pilula${quitadas === todas ? ' quitado' : ''}`;
        pilula.textContent = quitadas === todas ? (todas === 1 ? 'Quitada ✓' : 'Tudo quitado ✓')
            : lancadas > 0 ? `${quitadas} de ${todas} pagas`
            : (todas === 1 ? '1 a vencer' : `${todas} a vencer`);
        subtitulo.textContent = minha.temEstimado ? 'suas contas fixas por mês · com valores estimados' : 'suas contas fixas por mês';
        barra.setAttribute('aria-label', itens.map((i) => `${i.rotulo} ${brl(i.valor)}`).join(', '));
        const cor = (n) => `var(--serie-${(n % 5) + 1})`;
        barra.replaceChildren(...itens.map((i, n) => h('span', { style: `flex:${centavosDe(i.valor)} 1 0;background:${cor(n)}` })));
        legenda.replaceChildren(...itens.map((i, n) => h('span', {},
            h('span', { class: 'ponto', 'aria-hidden': 'true', style: `background:${cor(n)}` }),
            i.rotulo, ' ', h('strong', {}, brlCurto(i.valor)),
            h('span', { class: `situacao ${i.situacao.toLowerCase().replace('_', '-')}` }, ROTULO_SITUACAO[i.situacao]))));
        cartao.hidden = false;
    }

    api.minhaParteContasFixas(casaId).then((minha) => {
        if (state.casa?.id !== casaId) return; // saiu da conta antes de a resposta chegar
        crescer(cartao, () => mostrar(minha));
    }).catch(() => { /* sem o resumo, o card simplesmente não aparece */ });

    return cartao;
}

/** Contas fixas que ainda vão vencer, para dar tempo de se preparar antes de virarem despesa. */
function proximasContas(ctx) {
    const { state, irPara } = ctx;
    const casaId = state.casa.id;
    const corpo = h('div', { class: 'proximas-lista' });
    const cartao = h('section', { class: 'secao proximas', 'aria-labelledby': 'titulo-proximas', hidden: true },
        h('div', { class: 'secao-titulo' },
            h('h2', { id: 'titulo-proximas' }, 'Próximas contas'),
            h('button', { class: 'link', type: 'button', onclick: () => irParaContasFixas(irPara) }, 'Ver todas')),
        corpo);

    const diasAte = (iso) => Math.round((new Date(`${iso}T00:00:00`) - new Date(`${hojeISO()}T00:00:00`)) / 86400000);
    const quando = (dias) => (dias <= 0 ? 'hoje' : dias === 1 ? 'amanhã' : `em ${dias} dias`);

    function mostrar(contas) {
        const proximas = contas.filter((c) => c.ativa)
            .sort((a, b) => a.proximoVencimento.localeCompare(b.proximoVencimento)).slice(0, 4);
        if (proximas.length === 0) { cartao.hidden = true; return; }
        corpo.replaceChildren(...proximas.map((c) => {
            const euPago = c.pagador.id === state.eu.id;
            const minhaParte = c.valor / c.participantes.length; // aproximada: não considera quem paga junto
            const dias = diasAte(c.proximoVencimento);
            const esperando = c.aguardandoValor;
            const valorTexto = brl(euPago ? c.valor : minhaParte);
            const abrir = esperando && euPago ? () => abrirInformarValor(ctx, c, () => mostrar(state.contasFixas)) : null;
            return h('article', { class: `proxima${dias <= 3 || esperando ? ' urgente' : ''}`,
                    ...(abrir ? { role: 'button', tabindex: 0, onclick: abrir,
                        onkeydown: (e) => { if (e.key === 'Enter') abrir(); } } : {}) },
                h('span', { class: 'proxima-icone' }, icone(iconeDaConta(c.descricao), 18)),
                h('div', { class: 'nome' }, c.descricao),
                h('div', { class: 'quando' }, esperando ? 'venceu · falta o valor'
                    : `${quando(dias)} · ${dataBR(c.proximoVencimento, false)}`),
                h('strong', { class: 'valor num' }, c.valorVariavel ? `~${valorTexto}` : valorTexto),
                h('div', { class: 'dica' }, esperando
                    ? (euPago ? 'toque para informar o valor' : `${c.pagador.nome} vai informar`)
                    : `${c.valorVariavel ? 'estimado · ' : ''}${euPago ? 'você paga o total' : `sua parte · ${c.pagador.nome} paga`}`));
        }));
        cartao.hidden = false;
    }

    if (state.contasFixas) mostrar(state.contasFixas);
    api.listarContasFixas(casaId).then((contas) => {
        if (state.casa?.id !== casaId) return; // saiu da conta antes de a resposta chegar
        state.contasFixas = contas;
        atualizarAvisos(state);
        crescer(cartao, () => mostrar(contas));
    }).catch(() => { /* sem a lista, o card simplesmente não aparece */ });

    return cartao;
}

/** "Pra resolver agora": uma linha por pessoa, já compensando os dois sentidos, com a ação principal. */
function praResolver(ctx) {
    const { acertos } = ctx.state.resumo;
    return h('section', { 'aria-labelledby': 'titulo-resolver' },
        h('div', { class: 'resolver-titulo' },
            h('h2', { id: 'titulo-resolver' }, 'Pra resolver agora'),
            h('span', { class: 'resolver-contagem' }, String(acertos.length))),
        h('div', { class: 'resolver-lista' }, acertos.map((acerto) => linhaResolver(acerto, ctx))));
}

function linhaResolver(acerto, ctx) {
    const { state, recarregar } = ctx;
    const pessoa = acerto.pessoa;
    const deles = state.dividas.meDevem.filter((d) => d.pessoa.id === pessoa.id);
    const dele = state.dividas.euDevo.filter((d) => d.pessoa.id === pessoa.id);
    const vencimentos = (acerto.euDevo ? dele : deles).map((d) => d.vencimento).filter(Boolean).sort();
    const nome = acerto.euDevo ? pessoa.nome : nomeDoGrupo(state, pessoa);
    const quantas = acerto.itens.length;
    const sub = `${quantas === 1 ? acerto.itens[0].descricao : `${quantas} despesas`}`
        + (vencimentos.length > 0 ? ` · vence ${dataBR(vencimentos[0], false)}` : '');

    const cobrar = () => abrirCobranca({
        casaId: state.casa.id,
        despesa: { id: deles.length === 1 ? deles[0].despesaId : null,
            descricao: deles[0]?.descricao, pagador: state.eu },
        divisoes: deles.map((d) => ({ id: d.divisaoId, devedor: d.pessoa, valor: d.valor })),
        recarregar,
    });

    return h('div', { class: 'resolver-linha' },
        avatar(pessoa),
        h('div', { class: 'resolver-texto' },
            acerto.euDevo
                ? h('p', {}, 'Você deve ', h('strong', {}, brl(acerto.valor)), ` a ${nome}`)
                : h('p', {}, nome, nome.includes(' e ') ? ' te devem ' : ' te deve ', h('strong', {}, brl(acerto.valor))),
            h('p', { class: 'sub' }, sub)),
        acerto.euDevo
            ? h('button', { class: 'btn accent', type: 'button', onclick: () => abrirPagamento(pessoa, acerto) }, 'Pagar')
            : deles.length > 0 && h('button', { class: 'btn primary', type: 'button', onclick: cobrar }, 'Cobrar'));
}

/** O app não move dinheiro: orienta o pagamento e deixa o aviso pronto para mandar a quem recebe. */
function abrirPagamento(pessoa, acerto) {
    const aviso = `Oi, ${pessoa.nome}! Acabei de te pagar ${brl(acerto.valor)}. Confere aí pra mim? Valeu!`;
    let fechar;
    const campoAviso = h('textarea', { readOnly: true, 'aria-label': 'Aviso de pagamento' });
    campoAviso.value = aviso;
    const copiar = async () => {
        try { await navigator.clipboard.writeText(aviso); toast('Aviso copiado'); }
        catch { toast('Não consegui copiar. Selecione o texto e copie.', 'erro'); }
    };
    fechar = abrirModal({
        titulo: `Pagar ${pessoa.nome}`,
        corpo: h('div', { class: 'stack' },
            h('p', { class: 'pagar-valor num' }, brl(acerto.valor)),
            h('div', { class: 'pagar-itens' }, acerto.itens.map((i) => h('div', { class: 'linha-item' },
                h('span', {}, i.descricao), h('span', { class: 'num' }, brl(Math.abs(i.valor)))))),
            h('p', { class: 'muted' }, `Pague como combinarem (Pix, dinheiro). Quando ${pessoa.nome} confirmar `
                + 'o recebimento, a dívida some daqui.'),
            campoAviso),
        rodape: [
            h('button', { class: 'btn', type: 'button', onclick: () => fechar() }, 'Fechar'),
            h('button', { class: 'btn primary', type: 'button', onclick: copiar }, 'Copiar aviso'),
        ],
    });
}

function secaoDividas(ctx) {
    const { state, redesenhar } = ctx;
    const { euDevo, meDevem } = state.dividas;
    state.abaDividas ??= (euDevo.length > 0 || meDevem.length === 0) ? 'devo' : 'devem';
    const aba = state.abaDividas;
    const lista = aba === 'devo' ? euDevo : meDevem;

    const escolher = (nova) => () => {
        if (nova === aba) return;
        state.abaDividas = nova;
        redesenhar({ entrar: '.lista-dividas', direcao: nova === 'devem' ? 1 : -1 });
    };
    const seletor = h('div', { class: 'segmentado', role: 'tablist', 'aria-label': 'Minhas dívidas' },
        h('button', { type: 'button', role: 'tab', 'aria-selected': String(aba === 'devo'), onclick: escolher('devo') },
            `Eu devo (${euDevo.length})`),
        h('button', { type: 'button', role: 'tab', 'aria-selected': String(aba === 'devem'), onclick: escolher('devem') },
            `Me devem (${meDevem.length})`));

    return h('section', { class: 'secao', 'aria-label': 'Minhas dívidas' },
        seletor,
        lista.length === 0
            ? h('div', { class: 'card vazio lista-dividas' },
                h('strong', {}, aba === 'devo' ? 'Você não deve nada' : 'Ninguém te deve agora'),
                aba === 'devo' ? 'Quando alguém dividir uma despesa com você, ela aparece aqui.'
                    : 'Registre uma despesa e marque quem participa.')
            : h('div', { class: 'stack lista-dividas' }, agruparPorPessoa(lista).map((g) => grupoPessoa(g, aba === 'devem', ctx))),
        null);
}

/** Junta as dívidas de cada pessoa e soma em centavos. Quem tem mais a pagar/receber vem primeiro. */
function agruparPorPessoa(lista) {
    const grupos = new Map();
    for (const divida of lista) {
        const grupo = grupos.get(divida.pessoa.id) ?? { pessoa: divida.pessoa, dividas: [], centavos: 0 };
        grupo.dividas.push(divida);
        grupo.centavos += centavosDe(divida.valor);
        grupos.set(divida.pessoa.id, grupo);
    }
    return [...grupos.values()].sort((a, b) => b.centavos - a.centavos);
}

/** Um cartão por pessoa: o total no topo e, embaixo, cada dívida que compõe esse total. */
function grupoPessoa(grupo, souCredor, ctx) {
    const { pessoa, dividas, centavos } = grupo;
    const varias = dividas.length > 1;
    return h('article', { class: 'card grupo' },
        h('header', { class: 'grupo-topo' },
            avatar(pessoa),
            h('div', { class: 'info' },
                h('div', { class: 'titulo' }, souCredor ? nomeDoGrupo(ctx.state, pessoa) : `Para ${pessoa.nome}`),
                h('div', { class: 'sub' }, varias ? `${dividas.length} dívidas` : '1 dívida')),
            h('div', { class: 'valor num' }, brl(centavos / 100))),
        h('div', { class: 'grupo-lista' }, dividas.map((d) => linhaDivida(d, souCredor, varias, ctx))));
}

/** "Criada em 10/10 · vence 15/10" (ou "sem vencimento" enquanto ninguém cobrou com prazo). */
function datasDaDivida(divida) {
    const criada = dataBR(divida.criadaEm ?? divida.data, false);
    const vence = divida.vencimento ? `vence ${dataBR(divida.vencimento, false)}` : 'sem vencimento';
    return `Criada em ${criada} · ${vence}`;
}

function linhaDivida(divida, souCredor, mostrarValor, ctx) {
    const { state, recarregar } = ctx;
    const data = dataBR(divida.data, false);

    async function confirmarRecebimento() {
        const ok = await confirmar({
            titulo: 'Confirmar recebimento',
            mensagem: `Você recebeu ${brl(divida.valor)} de ${nomeDoGrupo(ctx.state, divida.pessoa)}?`,
            rotulo: 'Sim, recebi',
        });
        if (ok && await executar(() => api.registrarPagamento(divida.divisaoId), 'Pagamento registrado')) {
            await recarregar();
        }
    }

    const cobrar = () => abrirCobranca({
        casaId: state.casa.id,
        despesa: { id: divida.despesaId, descricao: divida.descricao, pagador: state.eu },
        divisoes: [{ id: divida.divisaoId, devedor: divida.pessoa, valor: divida.valor }],
        recarregar,
    });

    return h('div', { class: 'divida-linha' },
        h('div', { class: 'info' },
            h('div', { class: 'titulo' }, divida.descricao),
            h('div', { class: 'sub' }, datasDaDivida(divida)),
            badgeDivida(divida)),
        mostrarValor && h('div', { class: 'valor num' }, brl(divida.valor)),
        souCredor && h('div', { class: 'acoes' },
            h('button', { class: 'btn small', type: 'button', onclick: cobrar },
                divida.status === 'COBRADA' ? 'Cobrar de novo' : 'Cobrar'),
            h('button', { class: 'btn small accent', type: 'button', onclick: confirmarRecebimento }, 'Recebi')));
}

/** Coluna lateral (só no computador): quem mora na casa e o aviso de privacidade. */
export function viewLateral({ state }) {
    return [
        h('div', { class: 'privacidade' }, icone('cadeado', 20),
            h('span', {}, h('strong', {}, 'Privacidade. '),
                'Você só enxerga as despesas em que participa ou que você pagou. '
                + 'Dívidas entre os outros moradores não aparecem para você.')),
    ];
}
