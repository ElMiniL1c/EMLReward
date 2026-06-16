# EMLRewards

EMLRewards is a flexible rewards plugin for Paper Minecraft servers. It lets server owners create custom claimable rewards using simple configuration files. Rewards can give money, items, ranks, keys, commands, or anything supported by your server command system.

## Features

- Fully configurable rewards from `config.yml`
- Supports money, item, rank, and command-based rewards
- Claim rewards once or after a cooldown
- Add requirements such as playtime, level, world, or permissions
- Admin reset command for one reward or all rewards
- English and Spanish language files included
- Easy to add more languages
- Custom reward permissions with the `emlrew` prefix

## Commands

```text
/emlrew
/emlrew take <reward>
/emlrew reset <player> <reward|all>
/emlrew reload
```

## Permissions

```text
emlrew.admin
```

Custom reward permissions must start with:

```text
emlrew
```

Example:

```yaml
permission: "emlrew.rank.reward"
```

## Reward Types

The plugin includes three default examples:

- `money_example`: a money reward with a cooldown
- `item_example`: an item reward that can only be claimed once
- `rank_example`: a rank reward with a playtime requirement

You can delete, rename, or duplicate these examples to create your own rewards.

## Requirements

- Paper 1.21.1 or compatible
- Java 21

## Spanish

EMLRewards es un plugin flexible de recompensas para servidores Paper de Minecraft. Permite crear recompensas personalizadas desde `config.yml`, usando comandos del servidor.

Puedes crear recompensas de dinero, objetos, rangos, llaves, comandos personalizados o cualquier recompensa que tu servidor pueda ejecutar por comando.

Incluye soporte para:

- Recompensas de una sola vez
- Recompensas con cooldown
- Condiciones por tiempo jugado
- Condiciones por nivel
- Condiciones por mundo
- Condiciones por permiso
- Idiomas en ingles y espanol
- Reinicio de recompensas por admin

