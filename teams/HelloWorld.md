# Hello World

## Team Introduction

Hello World is responsible for the Main Menu.

Our goal is to develop the user interface for the game's main menu and other screens
so that the features implemented by other teams operate without any issues.

## Members

| Name | Role | GitHub |
| --- | --- | --- |
| Byeongjoo Hwang | Team leader, cross-team coordination, CI/CD | [eoieiie](https://github.com/eoieiie) |
| Junwoo Kang | Menu framework | [oofrog](https://github.com/oofrog) |
| Yongtae Kim | Version control, PR review, main repository access | [kyle891204](https://github.com/kyle891204) |
| Jaeone Park | Shop UI | [kyobak](https://github.com/kyobak) |
| Taehyun Bak | Achievements UI | [nuyh-99](https://github.com/nuyh-99) |
| Myeongho Song | Mouse support, key hints, exit confirmation | [SongMyeongHo](https://github.com/SongMyeongHo) |
| Changyong Woo | Testing, integration check | [samryong](https://github.com/samryong) |
| Hyeokjun Lee | Settings UI | [Aninnom](https://github.com/Aninnom) |
| Junhyeok Han | Documentation, wiki | [junh000](https://github.com/junh000) |

## Team Requirements

- Improve Main Menu usability
- Develop Settings UI
- Develop Achievements UI
- Develop Shop UI

## Detailed Requirements

* **Menu framework:** Replace the hard-coded options in `TitleScreen` with a menu item list so that other teams can add a screen by adding one item.
* **Mouse support:** Menu items can be selected by mouse click, with hover highlight.
* **Key hints & exit confirmation:** Footer showing available keys; Exit asks for confirmation.
* **Settings UI:** Volume controls (BGM/SFX) and key binding display.
* **Shop UI:** Display player currency, items, and purchase.
* **Achievements UI:** Layout showing locked and unlocked achievements.

## Dependencies on Other Teams

* **Shop UI:** Requires `Currency System` & `Item System`
* **Settings UI:** Requires `Sound Effects / BGM`
* **Achievements UI:** Requires `Records & Achievements System`
