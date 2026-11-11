// Barony's 3D battlefield (/battle), drawn by Strata through its web kit
// (strata_kit.h, from the Strata release tarball; see build.sh).
//
// The battle itself is simulated in the page (static/js/battle-sim.js), so
// this host is deliberately thin. Each frame the page writes:
//
//   bt_set_prims(rows, n)   n primitives, 10 floats each:
//                           kind, x, y, z, sx, sy, sz, r, g, b
//                           (kind is a STRATA_PRIMITIVE_*: soldiers, scenery)
//   bt_set_points(rows, n)  n soft points, 8 floats each:
//                           x, y, z, worldDiameter, r, g, b, a
//                           (dust, sparks)
//   bt_set_lights(rows, n)  n point lights, 8 floats each:
//                           x, y, z, range, r, g, b, intensity
//                           (torches at a castle, the avatar's glow)
//
// and moves the kit's orbit camera with sk_set_camera: a high orbit to
// watch, or a short (third-person) or near-zero (first-person) distance
// behind the head of the soldier the player has taken over.
//
// Everything is re-sent every frame, so a lost WebGL context needs nothing
// re-uploaded: strata_kit_engine_ready has no meshes or textures to restore.
#define STRATA_KIT_MAIN
#include "strata_kit.h"

#include <vector>

namespace {

std::vector<float> prims;
std::vector<float> points;
std::vector<float> lights;

constexpr int kPrimStride = 10;
constexpr int kPointStride = 8;
constexpr int kLightStride = 8;
constexpr int kMaxPrims = 20000;
constexpr int kMaxPoints = 4000;
constexpr int kMaxLights = 64;

void keep(std::vector<float>& into, const float* rows, int n, int stride, int max) {
    if (!rows || n <= 0) {
        into.clear();
        return;
    }
    if (n > max) n = max;
    into.assign(rows, rows + static_cast<size_t>(n) * stride);
}

}  // namespace

void strata_kit_configure(strata_kit::Options& o) {
    o.name = "barony-battle";
    o.fovDegrees = 60.0f;
    o.nearPlane = 0.05f;   // first person: the eye sits a few centimetres behind the head
    o.farPlane = 320.0f;
    o.shadowDistanceMin = 45.0f;
    o.fog = true;
}

void strata_kit_engine_ready(strata_kit::Host&) {}

void strata_kit_build(strata_kit::Host& h, double) {
    const size_t np = prims.size() / kPrimStride;
    h.items.reserve(h.items.size() + np);
    for (size_t i = 0; i < np; ++i) {
        const float* r = &prims[i * kPrimStride];
        h.add(static_cast<int>(r[0]), r[1], r[2], r[3], r[4], r[5], r[6], {r[7], r[8], r[9]});
    }
    const size_t nq = points.size() / kPointStride;
    for (size_t i = 0; i < nq; ++i) {
        const float* r = &points[i * kPointStride];
        h.point(r[0], r[1], r[2], 0.0f, r[3], {r[4], r[5], r[6]}, r[7]);
    }
    const size_t nl = lights.size() / kLightStride;
    for (size_t i = 0; i < nl; ++i) {
        const float* r = &lights[i * kLightStride];
        // The page sends the lights it wants kept first.
        h.light(r[0], r[1], r[2], r[3], {r[4], r[5], r[6]}, r[7], static_cast<int>(nl - i));
    }
}

STRATA_KIT_EXPORT void bt_set_prims(const float* rows, int n) { keep(prims, rows, n, kPrimStride, kMaxPrims); }
STRATA_KIT_EXPORT void bt_set_points(const float* rows, int n) { keep(points, rows, n, kPointStride, kMaxPoints); }
STRATA_KIT_EXPORT void bt_set_lights(const float* rows, int n) { keep(lights, rows, n, kLightStride, kMaxLights); }
