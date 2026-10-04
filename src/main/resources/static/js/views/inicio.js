import { api } from '../api.js';
import { confirmar, h } from '../ui.js';
import { avatar, badgeDivida, executar, icone } from '../components.js';
import { brl, dataBR } from '../format.js';
import { viewAcertos } from './acertos.js';
import { abrirCobranca } from './cobranca.js';
import { moradoresAtivos } from './moradores.js';

/** "Minha visão": só o que envolve quem está logado. Tudo vem das rotas /api/eu. */
export function viewInicio(ctx) {
    const { state } = ctx;
    const { resumo, dividas, eu } = state;
    const saldo = Math.round((resumo.aReceber - resumo.deve) * 100) / 100;
    const quite = resumo.acertos.length === 0 && dividas.euDevo.length === 0 && dividas.meDevem.length === 0;

    return h('div', { class: 'stack' },
        h('div', { class: 'saudacao' },
            h('h1', {}, `Oi, ${eu.nome}`),
            h('p', { class: 'muted' }, 'Aqui só aparece o que envolve você.')),

        h('div', { class: 'estatisticas' },
            h('section', { class: 'stat hero', 'aria-label': 'Seu saldo' },
                h('div', { class: 'rotulo' }, 'Seu saldo'),
                h('div', { class: 'valor num' }, saldo === 0 ? brl(0) : `${saldo < 0 ? '−' : '+'} ${brl(Math.abs(saldo))}`),
                h('div', { class: 'nota' }, saldo < 0 ? 'quanto você deve no total'
                    : saldo > 0 ? 'a seu favor, já descontando o que você deve' : 'nada pendente')),
            h('section', { class: 'stat', 'aria-label': 'Você deve' },
                h('div', { class: 'rotulo' }, 'Você deve'),
                h('div', { class: `valor num${resumo.deve > 0 ? ' negativo' : ''}` }, brl(resumo.deve))),
            h('section', { class: 'stat', 'aria-label': 'Você tem a receber' },
                h('div', { class: 'rotulo' }, 'A receber'),
                h('div', { class: `valor num${resumo.aReceber > 0 ? ' positivo' : ''}` }, brl(resumo.aReceber)))),

        quite
            ? h('div', { class: 'card tudo-quite' },
                h('strong', {}, 'Tudo quite por aqui'),
                h('span', { class: 'muted' }, 'Quando alguém registrar uma despesa com você, ela aparece aqui.'))
            : [resumo.acertos.length > 0 && viewAcertos(ctx), secaoDividas(ctx)]);
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
            : h('div', { class: 'stack' }, lista.map((d) => cartaoDivida(d, aba === 'devem', ctx))),
        aba === 'devo' && lista.length > 0
            && h('p', { class: 'dica' }, 'Quando você pagar, quem recebeu confirma no app.'));
}

function cartaoDivida(divida, souCredor, ctx) {
    const { state, recarregar } = ctx;
    const data = dataBR(divida.data, false);

    async function confirmarRecebimento() {
        const ok = await confirmar({
            titulo: 'Confirmar recebimento',
            mensagem: `Você recebeu ${brl(divida.valor)} de ${divida.pessoa.nome}?`,
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

    return h('article', { class: 'card divida' },
        avatar(divida.pessoa),
        h('div', { class: 'info' },
            h('div', { class: 'titulo' }, divida.descricao),
            h('div', { class: 'sub' }, souCredor ? `${divida.pessoa.nome} · ${data}` : `Para ${divida.pessoa.nome} · ${data}`),
            badgeDivida(divida)),
        h('div', { class: 'valor num' }, brl(divida.valor)),
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
