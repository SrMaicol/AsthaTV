package com.example.data

object SampleM3u {
    const val PLAYLIST = """#EXTM3U
#EXTINF:-1 tvg-id="hls_test" tvg-logo="https://upload.wikimedia.org/wikipedia/commons/thumb/c/c5/Big_buck_bunny_poster_big.jpg/800px-Big_buck_bunny_poster_big.jpg" group-title="Test HLS",Big Buck Bunny (HLS Stream)
https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8
#EXTINF:-1 tvg-id="hls_tos" tvg-logo="https://upload.wikimedia.org/wikipedia/commons/thumb/8/8f/Sintel_poster.jpg/800px-Sintel_poster.jpg" group-title="Test HLS",Tears of Steel (HLS Multi-Audio)
https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8
#EXTINF:-1 tvg-id="wiki_bunny" tvg-logo="https://upload.wikimedia.org/wikipedia/commons/thumb/c/c5/Big_buck_bunny_poster_big.jpg/800px-Big_buck_bunny_poster_big.jpg" group-title="Animation",Big Buck Bunny (MP4 WebM)
https://upload.wikimedia.org/wikipedia/commons/transcoded/c/c0/Big_Buck_Bunny_4K.webm/Big_Buck_Bunny_4K.webm.480p.vp9.webm
#EXTINF:-1 tvg-id="wiki_sintel" tvg-logo="https://upload.wikimedia.org/wikipedia/commons/thumb/8/8f/Sintel_poster.jpg/800px-Sintel_poster.jpg" group-title="Animation",Sintel Trailer (MP4 WebM)
https://upload.wikimedia.org/wikipedia/commons/transcoded/8/8f/Sintel_movie_4K.webm/Sintel_movie_4K.webm.480p.vp9.webm
"""
}
