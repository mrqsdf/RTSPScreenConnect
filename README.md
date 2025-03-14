# RTSP SCREEN CONNECT

This is a software create by Quentin Kupferlé, Pré-seller Technitien for [Sidev company](https://www.sidev.fr/).

With this software you can :
- Create Multi RTSP flux
- Chose screen to stream
- Chose custom FPS for stream

The streaming port for the first stream is : 5004 <p>
For all secondary stream, add 2, for exemple : screen 2 = 5006, screen 5 = 5012

The Protocol (TCP or UDP) is selected automatically according to first request third party 

Tested and work in :

| Company | TCP | UDP |
| -------- | ------- |-------|
| VLC | true | true |
| BrightSign | true | true |
| Novastar | fasle | false |

