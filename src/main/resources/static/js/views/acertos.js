import { h } from '../ui.js';
import { avatar } from '../components.js';
import { brl } from '../format.js';
import { nomeDoGrupo } from './moradores.js';

const sinal = (valor) => `${valor < 0 ? '−' : '+'} ${brl(Math.abs(valor))}`;

/** "O que fazer agora": por pessoa, quanto eu pago ou recebo, já compensando os dois sentidos. */
export function viewAcertos({ state }) {
    const { acertos } = state.resumo;

    return h('section', { class: 'card', 'aria-labelledby': 'titulo-acertos' },
        h('div', { class: 'card-head' }, h('h2', { id: 'titulo-acertos' }, 'O que fazer agora')),
        acertos.map((acerto) => h('div', { class: 'acerto' },
            h('div', { class: 'linha' },
                avatar(acerto.pessoa),
                h('div', { class: 'texto' },
                    acerto.euDevo
                        ? ['Pague a ', h('strong', {}, acerto.pessoa.nome)]
                        : [h('strong', {}, nomeDoGrupo(state, acerto.pessoa)), ' vai te pagar']),
                h('span', { class: `valor num ${acerto.euDevo ? 'negativo' : 'positivo'}` }, brl(acerto.valor))),
            // o detalhe só aparece quando ajuda: várias despesas ou compensação entre os dois sentidos
            acerto.itens.length > 1
                ? h('details', { class: 'detalhe num' },
                    h('summary', {}, 'Ver detalhes'),
                    acerto.itens.map((item) => h('div', {}, `${item.descricao} ${sinal(item.valor)}`)))
                : null)));
}
