import { api } from '../api.js';
import { confirmar, h } from '../ui.js';
import { avatar, badgeDivida, centavos as centavosDe, executar, icone } from '../components.js';
import { brl, dataBR, hojeISO } from '../format.js';
import { viewAcertos } from './acertos.js';
import { abrirCobranca } from './cobranca.js';
import { moradoresAtivos, nomeDoGrupo } from './moradores.js';

/** "Minha visão": só o que envolve quem está logado. Tudo vem das rotas /api/eu. */
export function viewInicio(ctx) {
    const { state } = ctx;
    const { resumo, dividas, eu } = state;
    const quite = resumo.acertos.length === 0 && dividas.euDevo.length === 0 && dividas.meDevem.length === 0;

    return h('div', { class: 'stack' },
        h('h1', { class: 'saudacao' }, `Oi, ${eu.nome}`),

        h('section', { class: 'resumo', 'aria-label': 'Seu resumo' },
            h('div', { class: 'resumo-titulo' }, 'Seu resumo'),
            h('div', { class: 'resumo-grade' },
                h('div', {}, h('div', { class: 'rotulo' }, 'Você deve'),
                    h('div', { class: 'valor num deve' }, brl(resumo.deve))),
                h('div', {}, h('div', { class: 'rotulo' }, 'A receber'),
                    h('div', { class: 'valor num receber' }, brl(resumo.aReceber))))),

        proximasContas(ctx),

        quite
            ? h('div', { class: 'card tudo-quite' },
                h('strong', {}, 'Tudo quite por aqui'),
                h('span', { class: 'muted' }, 'Nada a pagar nem a receber agora.'))
            : [resumo.acertos.length > 0 && viewAcertos(ctx), secaoDividas(ctx)]);
}

/** Contas fixas que ainda vão vencer, para dar tempo de se preparar antes de virarem despesa. */
function proximasContas({ state }) {
    const corpo = h('div', { class: 'proximas-lista' });
    const cartao = h('section', { class: 'card proximas', 'aria-labelledby': 'titulo-proximas', hidden: true },
        h('h2', { id: 'titulo-proximas' }, 'Próximas contas'), corpo);

    const diasAte = (iso) => Math.round((new Date(`${iso}T00:00:00`) - new Date(`${hojeISO()}T00:00:00`)) / 86400000);
    const quando = (dias) => (dias <= 0 ? 'hoje' : dias === 1 ? 'amanhã' : `em ${dias} dias`);

    api.listarContasFixas(state.casa.id).then((contas) => {
        const proximas = contas.filter((c) => c.ativa)
            .sort((a, b) => a.proximoVencimento.localeCompare(b.proximoVencimento)).slice(0, 4);
        if (proximas.length === 0) return;
        corpo.replaceChildren(...proximas.map((c) => {
            const euPago = c.pagador.id === state.eu.id;
            const minhaParte = c.valor / c.participantes.length; // aproximada: não considera quem paga junto
            const dias = diasAte(c.proximoVencimento);
            return h('div', { class: `proxima${dias <= 3 ? ' urgente' : ''}` },
                h('div', { class: 'esq' },
                    h('div', { class: 'nome' }, c.descricao),
                    h('div', { class: 'dica' }, `${dataBR(c.proximoVencimento, false)} · ${quando(dias)}`)),
                h('div', { class: 'direita' },
                    h('strong', { class: 'num' }, brl(euPago ? c.valor : minhaParte)),
                    h('div', { class: 'dica' }, euPago ? 'você paga o total' : `sua parte, ${c.pagador.nome} paga`)));
        }));
        cartao.hidden = false;
    }).catch(() => { /* sem a lista, o card simplesmente não aparece */ });

    return cartao;
}

function secaoDividas(ctx) {
    const { state, redesenhar } = ctx;
    const { euDevo, meDevem } = state.dividas;
    state.abaDividas ??= (euDevo.length > 0 || meDevem.length === 0) ? 'devo' : 'devem';
    const aba = state.abaDividas;
    const lista = aba === 'devo' ? euDevo : meDevem;

    const escolher = (nova) => () => { state.abaDividas = nova; redesenhar(); };
    const seletor = h('div', { class: 'segmentado', role: 'tablist', 'aria-label': 'Minhas dívidas' },
        h('button', { type: 'button', role: 'tab', 'aria-selected': String(aba === 'devo'), onclick: escolher('devo') },
            `Eu devo (${euDevo.length})`),
        h('button', { type: 'button', role: 'tab', 'aria-selected': String(aba === 'devem'), onclick: escolher('devem') },
            `Me devem (${meDevem.length})`));

    return h('section', { class: 'secao', 'aria-label': 'Minhas dívidas' },
        seletor,
        lista.length === 0
            ? h('div', { class: 'card vazio' },
                h('strong', {}, aba === 'devo' ? 'Você não deve nada' : 'Ninguém te deve agora'),
                aba === 'devo' ? 'Quando alguém dividir uma despesa com você, ela aparece aqui.'
                    : 'Registre uma despesa e marque quem participa.')
            : h('div', { class: 'stack' }, agruparPorPessoa(lista).map((g) => grupoPessoa(g, aba === 'devem', ctx))),
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
        h('section', { class: 'card', 'aria-labelledby': 'titulo-lateral' },
            h('h2', { id: 'titulo-lateral', style: 'margin-bottom:.5rem' }, state.casa.nome),
            moradoresAtivos(state).map((m) => h('div', { class: 'morador' },
                avatar(m), h('span', { class: 'nome' }, m.nome),
                m.id === state.eu.id ? h('span', { class: 'dica' }, 'você')
                    : m.admin ? h('span', { class: 'badge admin' }, 'admin') : null))),
        h('div', { class: 'privacidade' }, icone('cadeado', 20),
            h('span', {}, h('strong', {}, 'Privacidade. '),
                'Você só enxerga as despesas em que participa ou que você pagou. '
                + 'Dívidas entre os outros moradores não aparecem para você.')),
    ];
}
