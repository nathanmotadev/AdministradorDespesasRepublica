import { h } from '../ui.js';
import { avatar } from '../components.js';
import { brl } from '../format.js';

/** Card "Acertos pendentes": responde de bate-pronto "quem deve quanto a quem". */
export function viewAcertos({ state }) {
    const { acertos, saldos } = state.resumo;
    const comSaldo = saldos.filter((s) => s.aReceber > 0 || s.aPagar > 0);

    return h('section', { class: 'card', 'aria-labelledby': 'titulo-acertos' },
        h('div', { class: 'card-head' }, h('h2', { id: 'titulo-acertos' }, 'Acertos pendentes')),

        acertos.length === 0
            ? h('p', { class: 'tudo-quite' }, '✓ Tudo quite por aqui.')
            : acertos.map((acerto) => h('div', { class: 'acerto' },
                avatar(acerto.de),
                h('div', { class: 'texto' },
                    h('strong', {}, acerto.de.nome), ' deve ',
                    h('span', { class: 'valor num' }, brl(acerto.valor)),
                    ' a ', h('strong', {}, acerto.para.nome)))),

        comSaldo.length > 0 && h('div', { class: 'saldos' },
            comSaldo.map((saldo) => h('div', { class: 'saldo' },
                h('span', {}, saldo.morador.nome),
                h('span', { class: 'num' },
                    saldo.aReceber > 0 && h('span', { class: 'positivo' }, `+ ${brl(saldo.aReceber)}`),
                    saldo.aReceber > 0 && saldo.aPagar > 0 && ' · ',
                    saldo.aPagar > 0 && h('span', { class: 'negativo' }, `− ${brl(saldo.aPagar)}`))))));
}
