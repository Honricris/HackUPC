import json

with open('tags.json') as json_file:
    data = json.load(json_file)


def dfs(graph, inicio, destino, visitado=None, camino=None):
    if visitado is None:
        visitado = set()
    if camino is None:
        camino = []

    visitado.add(inicio)
    camino.append(inicio)

    if inicio == destino:
        return camino

    for vecino_index, vecino in enumerate(graph[inicio]["neighbours"]):
        if vecino is not None and vecino not in visitado:
            nuevo_camino = dfs(graph, vecino, destino, visitado, camino)
            if nuevo_camino:
                return nuevo_camino

    camino.pop()  # Retroceder si no se encuentra el destino desde este nodo
    return None

def find_next_movement(current, target) -> int:
    camino = dfs(data, current, target)
    if camino == None:
        raise ValueError()
    if len(camino) == 1:
        return -1

    if data[current]['neighbours'][0] == camino[1]:
        return 0
    if data[current]['neighbours'][1] == camino[1]:
        return 1
    if data[current]['neighbours'][2] == camino[1]:
        return 2
    if data[current]['neighbours'][3] == camino[1]:
        return 3
    else:
        return -1


last_movement = 0
movement = 0
while True:
    target_gate = 'G1'
    current = int(input('current position given by NFC: '))

    # Sacamos el id del target
    for i, item in enumerate(data):
        if item.get("gate") == target_gate:
            target = i
            break
    else:
        raise ValueError()

    last_movement = movement
    movement = find_next_movement(current, target)
    if movement == -1:
        print('Doneeee!')
        break
    print(['forward', 'right', 'back', 'left'][movement - last_movement])


