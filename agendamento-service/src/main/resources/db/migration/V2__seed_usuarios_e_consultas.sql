-- Massa inicial para avaliacao: um usuario por role (+ um segundo paciente,
-- usado para demonstrar o bloqueio de ownership entre pacientes).
-- Senha de todos: senha123  (hashes BCrypt, cost 10)

INSERT INTO usuario (id, nome, email, senha, role) VALUES
    (1, 'Dr. Carlos Andrade', 'medico@hospital.com',     '$2a$10$TTl0C9D0bJUsBdq3qtL7c.oYBaqyeS9Swg7sR7BmMp0SxlrcNdsY2', 'MEDICO'),
    (2, 'Ana Ribeiro',        'enfermeiro@hospital.com', '$2a$10$qZako4sKau.Jjyut/qK/C.zh0c8SlWWAlGei4c1.GuRi11yKyTnJ2', 'ENFERMEIRO'),
    (3, 'Joao Souza',         'paciente@hospital.com',   '$2a$10$2ReXdBxxzbsPE0bDG6B1yepqxl85bbmNAzaInsQaGD9jwrQJJ7W1S', 'PACIENTE'),
    (4, 'Maria Lima',         'paciente2@hospital.com',  '$2a$10$2ReXdBxxzbsPE0bDG6B1yepqxl85bbmNAzaInsQaGD9jwrQJJ7W1S', 'PACIENTE');

INSERT INTO medico (id, usuario_id, crm, especialidade) VALUES
    (1, 1, 'CRM-SP-123456', 'Cardiologia');

INSERT INTO paciente (id, usuario_id, cpf, telefone, data_nascimento) VALUES
    (1, 3, '111.111.111-11', '(11) 98888-1111', DATE '1990-05-12'),
    (2, 4, '222.222.222-22', '(11) 97777-2222', DATE '1985-11-30');

-- Consultas do paciente 1: uma passada (historico) e duas futuras (agenda/lembretes).
INSERT INTO consulta (id, paciente_id, medico_id, data_hora, status, observacoes, criado_em, atualizado_em) VALUES
    (1, 1, 1, CURRENT_TIMESTAMP - INTERVAL '30' DAY, 'REALIZADA', 'Consulta de rotina; pressao controlada.', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2, 1, 1, CURRENT_TIMESTAMP + INTERVAL '2' DAY,  'AGENDADA',  'Retorno para avaliacao de exames.',       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (3, 1, 1, CURRENT_TIMESTAMP + INTERVAL '20' DAY, 'AGENDADA',  'Acompanhamento trimestral.',              CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    -- Consulta da Maria: o Joao NUNCA pode enxergar esta linha.
    (4, 2, 1, CURRENT_TIMESTAMP + INTERVAL '5' DAY,  'AGENDADA',  'Primeira avaliacao cardiologica.',        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Os IDs acima foram inseridos explicitamente; reposiciona as sequences de identidade
-- para que os proximos inserts gerados pela aplicacao nao colidam.
ALTER TABLE usuario  ALTER COLUMN id RESTART WITH 5;
ALTER TABLE medico   ALTER COLUMN id RESTART WITH 2;
ALTER TABLE paciente ALTER COLUMN id RESTART WITH 3;
ALTER TABLE consulta ALTER COLUMN id RESTART WITH 5;
