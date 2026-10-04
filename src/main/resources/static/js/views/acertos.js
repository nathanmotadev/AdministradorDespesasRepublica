import { h } from '../ui.js';
import { avatar } from '../components.js';
import { brl } from '../format.js';

const sinal = (valor) => `${valor < 0 ? '−' : '+'} ${brl(Math.abs(valor))}`;

/** "O que fazer agora": por pessoa, quanto eu pago ou recebo, já compensando os dois sentidos. */
export function viewAcertos({ state }) {
    const { acertos } = state.resumo;

    return h('section', { class: 'card', 'aria-labelledby': 'titulo-acertos' },
        h('div', { class: 'card-head' }, h('h2', { id: 'titulo-acertos' }, 'O que fazer agora')),
        h('p', { class: 'dica', style: 'margin:-.4rem 0 .6rem' }, 'Dívidas nos dois sentidos já foram compensadas.'),
        acertos.map((acerto) => h('div', { class: 'acerto' },
            h('div', { class: 'linha' },
                avatar(acerto.pessoa),
                h('div', { class: 'texto' },
                    acerto.euDevo
                        ? ['Pague a ', h('strong', {}, acerto.pessoa.nome)]
                        : [h('strong', {}, acerto.pessoa.nome), ' vai te pagar']),
                h('span', { class: `valor num ${acerto.euDevo ? 'negativo' : 'positivo'}` }, brl(acerto.valor))),
            // o detalhe só aparece quando ajuda: várias despesas ou compensação entre os dois sentidos
            acerto.itens.length > 1
                ? h('div', { class: 'detalhe num' },
                    acerto.itens.map((item) => `${item.descricao} ${sinal(item.valor)}`).join(' · '))
                : null)));
}
