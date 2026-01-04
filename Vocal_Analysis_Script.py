import io
import json
import math
import sys
from pathlib import Path
from sqlite3 import Binary
from typing import Optional
import matplotlib
import pandas as pd
import joblib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np
import parselmouth
import sqlite3
from pathlib import Path

FILE_PATH = None
PREVIOUS_TIME: Optional[float] = None
PREVIOUS_FREQ_F0: Optional[float] = None
PREVIOUS_FREQ_F1: Optional[float] = None
PREVIOUS_FREQ_F2: Optional[float] = None
PREVIOUS_FREQ_F3: Optional[float] = None
PREVIOUS_FREQ_F4: Optional[float] = None
UNVOICE_DB = -200.0


def _reset_track_state():
    """
    Resets the global variables to default state.

    """
    global PREVIOUS_TIME, PREVIOUS_FREQ_F0, PREVIOUS_FREQ_F1, PREVIOUS_FREQ_F2, PREVIOUS_FREQ_F3, PREVIOUS_FREQ_F4
    PREVIOUS_TIME = None
    PREVIOUS_FREQ_F0 = PREVIOUS_FREQ_F1 = PREVIOUS_FREQ_F2 = PREVIOUS_FREQ_F3 = PREVIOUS_FREQ_F4 = None


# list[[time_0, f0_0, f1_0, f2_0, f3_0, f4_0], [time_1, f0_1, f1_1, f2_1, f3_1, f4_1]]
def filter_frequency_synchronized_patch(data: list[list[float]]) -> list[list[float]]:
    """
    Filters anomalous frequency data and unvoiced segments.

    :param data: A matrix of raw vocal frequency data.
    :return: A matrix of filtered vocal frequency data.
    """
    global PREVIOUS_FREQ_F0, PREVIOUS_FREQ_F1, PREVIOUS_FREQ_F2, PREVIOUS_FREQ_F3, PREVIOUS_FREQ_F4, PREVIOUS_TIME
    post_filter = []
    for i in range(len(data)):
        sub_data = data[i]
        time = sub_data[0]  # type: float
        f0 = sub_data[1]  # type: float
        f1 = sub_data[2]  # type: float
        f2 = sub_data[3]  # type: float
        f3 = sub_data[4]  # type: float
        f4 = sub_data[5]  # type: float

        # Initialize the previous frequencies
        if PREVIOUS_TIME is None:
            PREVIOUS_FREQ_F0 = f0
            PREVIOUS_FREQ_F1 = f1
            PREVIOUS_FREQ_F2 = f2
            PREVIOUS_FREQ_F3 = f3
            PREVIOUS_FREQ_F4 = f4

        # Skips if the pitch is zero
        if f0 is None or f0 <= 0:
            continue

        f0_ok = _filter_helper("f0", f0, time)
        f1_ok = _filter_helper("f1", f1, time)
        f2_ok = _filter_helper("f2", f2, time)
        f3_ok = _filter_helper("f3", f3, time)
        f4_ok = _filter_helper("f4", f4, time)

        low_ok_count = int(f1_ok) + int(f2_ok) + int(f3_ok) + int(f4_ok)

        valid = f0_ok and low_ok_count > 2

        if valid:
            post_filter.append([time, f0, f1, f2, f3, f4])
            PREVIOUS_FREQ_F0 = f0
            PREVIOUS_FREQ_F1 = f1
            PREVIOUS_FREQ_F2 = f2
            PREVIOUS_FREQ_F3 = f3
            PREVIOUS_FREQ_F4 = f4
            PREVIOUS_TIME = time

    return post_filter


def _filter_helper(formant: str, frequency: float, time: Optional[float] = None):
    """
    Helper function that determines if a frequency is within the bounds of the formant.

    :param formant: The formant the frequency is being checked against.
    :param frequency: The frequency that's being checked.
    :param time: The time that corresponds to the frequency.
    :return: True if frequency is within the bounds of the formant.
    False otherwise.
    """
    global PREVIOUS_FREQ_F0, PREVIOUS_FREQ_F1, PREVIOUS_FREQ_F2, PREVIOUS_FREQ_F3, PREVIOUS_FREQ_F4, PREVIOUS_TIME

    # invalid current value
    if frequency is None or frequency <= 0:
        return False

    # optional spacing gate (ignore frames closer than 20 ms to the previous)
    if (time is not None and PREVIOUS_TIME is not None and
            time > PREVIOUS_TIME and
            abs(time - PREVIOUS_TIME) <= 0.02):
        return False

    form = formant.casefold()

    # thresholds (continuity) in semitones and plausibility ranges in Hz
    # tune if needed
    ranges = {
        "f0": (None, 75.0, 600.0),  # (th set below), hard range gate for pitch
        "f1": (5.0, 250.0, 950.0),
        "f2": (7.0, 700.0, 3300.0),
        "f3": (8.0, 1500.0, 3700.0),
        "f4": (9.0, 2700.0, 5000.0),
    }

    if form not in ranges:
        return False

    fth, lo, hi = ranges[form]

    # select previous value
    if form == "f0":
        prev_freq = PREVIOUS_FREQ_F0
        # hard range gate for pitch
        if frequency <= lo or frequency >= hi:
            return False

        # if we have a valid previous, enforce jump rate and continuity
        if prev_freq is not None and float(prev_freq) > 0:
            # jump-size in semitones
            st = 12.0 * abs(math.log2(frequency / float(prev_freq)))

            # jump-rate gate: >= 2 octaves in < 1s -> > 24 st/s
            if time is not None and PREVIOUS_TIME is not None and time > PREVIOUS_TIME:
                dt = time - PREVIOUS_TIME
                if dt > 0 and (st / dt) > 24.0:
                    return False

            # continuity gate for F0 (tunable; default 4 st)
            if st > 4.0:
                return False

        # if no previous, accept current (caller should update PREVIOUS_* and PREVIOUS_TIME on accept)
        return True

    else:
        # formants F1...F4: plausibility gate first
        if frequency <= lo or frequency >= hi:
            return False

        # pick previous for this track
        if form == "f1":
            prev_freq = PREVIOUS_FREQ_F1
        elif form == "f2":
            prev_freq = PREVIOUS_FREQ_F2
        elif form == "f3":
            prev_freq = PREVIOUS_FREQ_F3
        else:  # f4
            prev_freq = PREVIOUS_FREQ_F4

        # no previous -> accept
        if prev_freq is None or float(prev_freq) <= 0:
            return True

        # continuity in semitones
        st = 12.0 * abs(math.log2(frequency / float(prev_freq)))
        return st <= fth


def get_freq_medians(freq_data: list[float]) -> float:
    """
    Get the median frequency for the designated formant frequency band.

    :param freq_data: List of frequencies extracted from the sample
    :return: The median formant.
    """

    vals = [x for x in freq_data if x is not None and x > 0 and math.isfinite(x)]
    vals.sort()
    mid_index = len(vals) // 2

    if (len(vals) % 2) == int(1):
        return vals[mid_index]
    else:

        return (vals[mid_index] + vals[mid_index - 1]) / 2


def _hz_to_semitones(hz, ref=55.0):
    """
    Converts Hz to semitones.
    :param hz: The hz to convert to semitone.
    :param ref: The zero point of which what frequency counts as 0 semitones.
    Default is 55 Hz.
    :return: Returns the number of semitones that corresponds to the given frequency in Hz.
    """
    hz = np.asanyarray(hz, dtype=float)
    hz = hz[hz > 0]
    return 12.0 * np.log2(hz / ref)


def _semitones(freq_list_1: list[float], freq_list_2: list[float]):
    """
    Computes the difference in semitones between two frequency arrays.

    :param freq_list_1: The first frequency array.
    :param freq_list_2: The second frequency array.
    :return: Array of frequency differences.
    """
    one_octave = 12.0
    freq_list_1 = np.asarray(freq_list_1, float)
    freq_list_2 = np.asarray(freq_list_2, float)
    mask = (freq_list_1 > 0) & (freq_list_2 > 0)
    out = np.full(np.broadcast(freq_list_1, freq_list_2).shape, np.nan, dtype=float)
    out[mask] = one_octave * np.log2(freq_list_1[mask] / freq_list_2[mask])
    return out


def _pitch_stats(f0_hz: list[float], t_s: list[float], floor=75.0, ceil=600.0):
    """
    Gets the pitch statistics of the f0 frequency list.

    :param f0_hz:
    :param t_s:
    :param floor:
    :param ceil:
    :return:
    """
    f0 = np.asarray(f0_hz, float)
    t = np.asarray(t_s, float)
    voiced = np.isfinite(f0) & (f0 >= floor) & (f0 <= ceil)
    f0 = f0[voiced]
    t = t[voiced]
    if f0.size < 5:
        return None
    st = _hz_to_semitones(f0)
    a = np.c_[t, np.ones_like(t)]
    m, _ = np.linalg.lstsq(a, st, rcond=None)[0]
    p5, p95 = np.percentile(f0, [5, 95])
    return {
        "f0_mean_hz": float(np.mean(f0)),
        "f0_sd_st": float(np.std(st, ddof=1)) if st.size > 1 else 0.0,
        "f0_min_hz": float(np.min(f0)),
        "f0_max_hz": float(np.max(f0)),
        "f0_p5_hz": float(p5),
        "f0_p95_hz": float(p95),
        "range_semitones": float(_semitones(np.max(f0), np.min(f0))) if np.min(f0) > 0 else np.nan,
        "range_st_5_95": float(_semitones(p95, p5)) if p5 > 0 else np.nan,
        "slope_st_per_sec": float(m),
    }


def extract_breathiness_and_intonation(
        sound: parselmouth.Sound,
        *,
        time_step: float = 10,
        pitch_floor: float = 75.0,
        pitch_ceiling: float = 600) -> dict:
    """
    Extracts the breathiness and intonation from the raw vocal sample.
    :param sound: Raw vocal sample.
    :param time_step: The number of steps in Ms. Default is 10 ms
    :param pitch_floor:
    :param pitch_ceiling:
    :return:
    """
    time_step = time_step / 1000
    pitch = sound.to_pitch(time_step=time_step, pitch_floor=pitch_floor, pitch_ceiling=pitch_ceiling)
    f0 = pitch.selected_array['frequency']
    times = pitch.xs()

    # ---- Intonation block ----
    voiced_mask = f0 > 0
    f0_v = f0[voiced_mask]
    t_v = times[voiced_mask]

    voiced_frames = int(f0_v.size)
    total_frames = int(f0.size)
    voiced_ratio = (voiced_frames / total_frames) if total_frames else np.nan

    stats = _pitch_stats(f0_v, t_v, floor=pitch_floor, ceil=pitch_ceiling)
    if stats is None:
        intonation = dict(
            f0_mean_hz=np.nan, f0_sd_hz=np.nan, f0_min_hz=np.nan, f0_max_hz=np.nan,
            range_semitones=np.nan, slope_st_per_sec=np.nan, voiced_frac=np.nan
        )
    else:
        intonation = dict(
            f0_mean_hz=stats["f0_mean_hz"],
            f0_sd_hz=float(np.std(f0_v, ddof=1)) if f0_v.size > 1 else 0.0,
            f0_min_hz=stats["f0_min_hz"],
            f0_max_hz=stats["f0_max_hz"],
            range_semitones=stats["range_semitones"],
            slope_st_per_sec=stats["slope_st_per_sec"],
            f0_p5_hz=stats["f0_p5_hz"],
            f0_p95_hz=stats["f0_p95_hz"],
            range_st_5_95=stats["range_st_5_95"],
            f0_sd_st=stats["f0_sd_st"],
            voiced_frac=voiced_ratio
        )

    # ---- Breathiness block ----
    harm = sound.to_harmonicity_cc(time_step=time_step, minimum_pitch=pitch_floor, periods_per_window=1.0)
    hnr_raw = harm.values.ravel()  # includes UNVOICE_DB for unvoiced
    hnr_total = int(hnr_raw.size)
    hnr_voiced_mask = (hnr_raw != UNVOICE_DB) & np.isfinite(hnr_raw)
    hnr_voiced_frames = int(np.sum(hnr_voiced_mask))
    hnr_voiced_fraction = (hnr_voiced_frames / hnr_total) if hnr_total else np.nan

    hnr_vals = hnr_raw[hnr_voiced_mask]  # use voiced-only values for stats
    breathiness = dict(
        hnr_mean_db=float(np.mean(hnr_vals)) if hnr_vals.size else np.nan,
        hnr_median_db=float(np.median(hnr_vals)) if hnr_vals.size else np.nan,
        hnr_frames_total=hnr_total,
        hnr_voiced_frames=hnr_voiced_frames,
        hnr_voiced_fraction=float(hnr_voiced_fraction)
    )

    return {"breathiness": breathiness, "intonation": intonation}


# -----------------Summarizes the formants per file----------------

def summarize_formants(filtered_rows: list[list[float]]) -> dict:
    """
    Converts the formant matrix into a usable dictionary to run through the prediction model.
    :param filtered_rows: The post filtered list of formants
    :return: A dictionary of the filtered formants median and ratios.
    """

    if not filtered_rows:
        return {
            "F0_med": np.nan, "F0_p5": np.nan, "F0_p95": np.nan,
            "F1_med": np.nan, "F2_med": np.nan, "F3_med": np.nan, "F4_med": np.nan,
            "F2_over_F1": np.nan, "F3_over_F2": np.nan, "F4_over_F3": np.nan
        }

    arr = np.asarray(filtered_rows, float)
    f0, f1, f2, f3, f4 = arr[:, 1], arr[:, 2], arr[:, 3], arr[:, 4], arr[:, 5]

    def safe_med(x):
        x = x[(x > 0) & np.isfinite(x)]
        return float(np.median(x)) if x.size else np.nan

    out = {
        "F0_med": safe_med(f0),
        "F0_p5": float(np.percentile(f0[f0 > 0], 5)) if np.any(f0 > 0) else np.nan,
        "F0_p95": float(np.percentile(f0[f0 > 0], 95)) if np.any(f0 > 0) else np.nan,
        "F1_med": safe_med(f1),
        "F2_med": safe_med(f2),
        "F3_med": safe_med(f3),
        "F4_med": safe_med(f4)
    }

    # ratios capture spacing/ brightness; guard divides
    out["F2_over_F1"] = (out["F2_med"] / out["F1_med"]) if (out["F1_med"] and np.isfinite(out["F1_med"])) else np.nan
    out["F3_over_F2"] = (out["F3_med"] / out["F2_med"]) if (out["F2_med"] and np.isfinite(out["F2_med"])) else np.nan
    out["F4_over_F3"] = (out["F4_med"] / out["F3_med"]) if (out["F3_med"] and np.isfinite(out["F3_med"])) else np.nan
    return out


# ---- Flatten the intonation + breathiness dicts

def flatten_features(breathiness: dict, intonation: dict) -> dict:
    """
    Combines the breathiness index dictionary and the intonation diction into a single dictionary.
    Used to run through the logistic regression trained model.
    :param breathiness:
    :param intonation:
    :return:
    """
    # enforce monotonic "breathiness_index" (higher = breathier)

    hnr_mean = breathiness.get("hnr_mean_db", np.nan)
    # higher = breathier
    breathiness_index = -hnr_mean if (hnr_mean is not None and np.isfinite(hnr_mean)) else np.nan

    flat = {
        # Intonation
        "f0_mean_hz": intonation.get("f0_mean_hz", np.nan),
        "f0_sd_hz": intonation.get("f0_sd_hz", np.nan),
        "f0_min_hz": intonation.get("f0_min_hz", np.nan),
        "f0_max_hz": intonation.get("f0_max_hz", np.nan),
        "f0_p5_hz": intonation.get("f0_p5_hz", np.nan),
        "f0_p95_hz": intonation.get("f0_p95_hz", np.nan),
        "range_semitones": intonation.get("range_semitones", np.nan),
        "range_st_5_95": intonation.get("range_st_5_95", np.nan),
        "slope_st_per_sec": intonation.get("slope_st_per_sec", np.nan),
        "f0_sd_st": intonation.get("f0_sd_st", np.nan),
        "voiced_frac": intonation.get("voiced_frac", np.nan),

        # Breathiness / HNR
        "hnr_mean_db": hnr_mean,
        "hnr_median_db": breathiness.get("hnr_median_db", np.nan),
        "hnr_voiced_fraction": breathiness.get("hnr_voiced_fraction", np.nan),
        "breathiness_index": breathiness_index,
    }

    return flat


# ---- 3) end-to-end: turn one audio file -> one feature row ----
def feature_for_file(sound: parselmouth.Sound,
                     data_rows: list[list[float]],
                     file_id: str) -> dict:
    """
    Generates a feature list, used for generating the CSV file to run through the ML algo.

    :param sound: The users vocal sample
    :param data_rows: The post filtered matrix of frequency derived from the sound object
    :param file_id: Unique id for identifying the sample feature list
    :return: A dictionary of the feature list.
    """
    formant_feats = summarize_formants(data_rows)

    # intonation and breathiness index
    ibi = extract_breathiness_and_intonation(sound)

    # flatten and merge
    flat_ib = flatten_features(ibi["breathiness"], ibi["intonation"])
    row = {
        "file_id": file_id,
    }

    row.update(formant_feats)
    row.update(flat_ib)

    return row


def _feature_for_file(sound: parselmouth.Sound,
                      data_rows: list[list[float]],
                      file_id: str,
                      label) -> dict:
    """
    [Used for training]\n
    Generates a feature list, used for generating the CSV file to run through the ML algo.

    :param sound: The users vocal sample
    :param data_rows: The post filtered matrix of frequency derived from the sound object
    :param file_id: Unique id for identifying the sample feature list
    :param label: Labels the dependent variable.
    0 = Masculine, 1 = Feminine.
    :return: A dictionary of the feature list.
    """
    formant_feats = summarize_formants(data_rows)

    # intonation and breathiness index
    ibi = extract_breathiness_and_intonation(sound)

    # flatten and merge
    flat_ib = flatten_features(ibi["breathiness"], ibi["intonation"])
    row = {
        "file_id": file_id,
    }

    row.update(formant_feats)
    row.update(flat_ib)
    row.update(["label", label])

    return row


def plot_formants(time_: list[float], f0_: list[float],
                  f1_: list[float], f2_: list[float],
                  f3_: list[float], f4_: list[float]) -> bytes:
    """
    Generates a dot graph of all the different formant and returns

    :param time_: The list of time sequence
    :param f0_: The list of pitch
    :param f1_: The list of Formants 1
    :param f2_: The list of Formant 2
    :param f3_: The list of Formant 3
    :param f4_: The list of Formant 4
    return: the bytes of the plot.
    """

    ticks1 = np.arange(0, 1001, 100)
    ticks2 = np.arange(1000, 8001, 500)
    yticks = np.unique(np.concatenate([ticks1, ticks2]))

    fig, ax = plt.subplots(figsize=(6, 12), dpi=300)

    ax.plot(time_, f0_, label="Pitch", color="black")
    ax.set_yticks(yticks)
    ax.minorticks_on()
    ax.scatter(time_, f1_, s=5, label="F1")
    ax.scatter(time_, f2_, s=5, label="F2")
    ax.scatter(time_, f3_, s=5, label="F3")
    ax.scatter(time_, f4_, s=5, label="F4")
    ax.set_xlabel("Times (s)")
    ax.set_ylabel("Frequency (Hz)")
    ax.set_ybound(0, 5501)
    ax.grid(True)
    ax.legend(loc="upper left", frameon=True)
    buf = io.BytesIO()
    fig.savefig(buf, format="png", bbox_inches="tight")
    plt.close(fig)
    return buf.getvalue()


def connect_table():
    """
    Generate an SQLite3 table that stores chart data for GUI, f0-f4 frequency data, and formant mean data.
    """
    conn = sqlite3.connect("Vocal_Analysis.db")
    cursor = conn.cursor()

    cursor.execute("""
                   CREATE TABLE IF NOT EXISTS user_formants
                   (
                       id               INTEGER PRIMARY KEY AUTOINCREMENT,
                       timestamp        TIMESTAMP DEFAULT (datetime('now', 'localtime')),
                       time_json        TEXT NOT NULL CHECK (json_valid(time_json)),
                       f0_json          TEXT NOT NULL CHECK (json_valid(f0_json)),
                       f1_json          TEXT NOT NULL CHECK (json_valid(f1_json)),
                       f1_med           REAL NOT NULL CHECK (f1_med >= 0),
                       f2_json          TEXT NOT NULL CHECK (json_valid(f2_json)),
                       f2_med           REAL NOT NULL CHECK (f2_med >= 0),
                       f3_med           REAL NOT NULL CHECK (f3_med >= 0),
                       f4_med           REAL NOT NULL CHECK (f4_med >= 0),
                       formant_med_json TEXT NOT NULL CHECK (json_valid(formant_med_json)),
                       scatter_plot     BLOB NOT NULL,
                       gender_label     TEXT NOT NULL CHECK (gender_label IN (
                                                                              'MASC', 'FEMME', 'ANDRO_MASC',
                                                                              'ANDRO_FEMME', 'ANDRO', 'FEMME_FALSETTO',
                                                                              'MASC_FALSETTO', 'ANDRO_FALSETTO')
                           ),
                       gender_score     REAL NOT NULL CHECK (gender_score >= 0 AND gender_score <= 1)
                   )


                   """)
    conn.commit()
    conn.close()


def insert_to_table(time_: list[float], f0_: list[float], f1_: list[float], f1_med: float, f2_: list[float],
                    f2_med: float, f3_med: float, f4_med: float, formant_med: list[float], png_bytes: bytes,
                    gender_label: str,
                    gender_score: float) -> None:
    """
    Inserts the formant data (filtered and average) into the SQL database and generates a plot.
    Each element of formant corresponds to the time stamp in the list of time sequence.





    :param time_: The list of time sequence
    :param f0_: The list of pitch
    :param f1_: The list of Formants 1
    :param f1_med: The F1 median
    :param f2_: The list of Formant 2
    :param f2_med: The F2 Median
    :param f3_med: The median of Formant 3
    :param f4_med: The median of Formant 4
    :param formant_med: List of medians for formants (F0-F4)
    :param png_bytes: The scatter plot
    :param gender_label:
    :param gender_score:
    :return: None
    """

    payload = (
        json.dumps(list(map(float, time_))),
        json.dumps(list(map(float, f0_))),
        json.dumps(list(map(float, f1_))),
        float(f1_med) if f1_med is not None else None,
        json.dumps(list(map(float, f2_))),
        float(f2_med) if f2_med is not None else None,
        float(f3_med) if f3_med is not None else None,
        float(f4_med) if f4_med is not None else None,
        json.dumps(list(map(float, formant_med))),
        Binary(png_bytes),
        gender_label,
        float(gender_score) if gender_score is not None else None,
    )

    conn = sqlite3.connect("Vocal_Analysis.db")
    cur = conn.cursor()
    cur.execute("""
                INSERT INTO user_formants(time_json, f0_json, f1_json, f1_med, f2_json, f2_med, f3_med, f4_med,
                                          formant_med_json,
                                          scatter_plot, gender_label, gender_score)
                VALUES (json(?), json(?), json(?), ?, json(?), ?, ?, ?, json(?), ?, ?, ?)
                """, payload)
    conn.commit()
    conn.close()


def _create_csv(row: dict) -> None:
    """
    Exports the feature list to a CSV file.
    :param row: The feature list.
    :return: None
    """
    df = pd.DataFrame([row])
    base_dir = Path.home() / "VocalAnalysisTool"


    # 1) User home app folder
    home_csv = Path.home() / "VocalAnalysisTool" / "user_features.csv"
    home_csv.parent.mkdir(parents=True, exist_ok=True)
    df.to_csv(home_csv, mode="w", header=True, index=False)

    # 2) Project/runtime folder
    base_csv = Path(base_dir) / "user_features.csv"
    base_csv.parent.mkdir(parents=True, exist_ok=True)
    df.to_csv(base_csv, mode="w", header=True, index=False)

    print(f"CSV has been generated:\n- {home_csv}\n- {base_csv}")


def _pitch_spike_trap_guardrail(data_frame: pd.DataFrame) -> bool:
    """
    Guardrail: blocks false-femme when pitch spikes occur on top of
    masculine resonance (low F1) + harsh/noisy/low-voiced signal.

    Returns True when we should override a FEMME decision.
    """
    try:
        f1_med = float(data_frame["F1_med"].iloc[0])
        f0_med = float(data_frame["F0_med"].iloc[0])
        f0_p95 = float(data_frame["F0_p95"].iloc[0])
        f0_max = float(data_frame["f0_max_hz"].iloc[0])
        range_semitones = float(data_frame["range_semitones"].iloc[0])
        voiced_frac = float(data_frame["voiced_frac"].iloc[0])
        hnr_mean_db = float(data_frame["hnr_mean_db"].iloc[0])
    except Exception:
        return False

    # 1) masculine resonance anchor (absolute F1 is a strong cue)
    masc_anchor = np.isfinite(f1_med) and (f1_med <= 410.0)

    # 2) noisy / weak voicing (not femme-coded acoustically)
    noisy_or_unvoiced = (
            (np.isfinite(hnr_mean_db) and hnr_mean_db <= 12.5)
            or (np.isfinite(voiced_frac) and voiced_frac <= 0.50)
    )

    # 3) pitch spike behavior that fools classifiers
    pitch_spike = (
            (np.isfinite(f0_max) and f0_max >= 430.0)
            or (np.isfinite(range_semitones) and range_semitones >= 26.0)
            or (np.isfinite(f0_p95) and f0_p95 >= 225.0)
    )

    # 4) don't block clearly-femme pitch centers
    not_clearly_femme_pitch = np.isfinite(f0_med) and (f0_med < 220.0)

    return bool(masc_anchor and noisy_or_unvoiced and pitch_spike and not_clearly_femme_pitch)


def _soft_masc_androgyny_guardrail(data_frame: pd.DataFrame, masc_value: float, femme_value: float) -> bool:
    """
    Guardrail: when the model says MASC confidently, but acoustics read
    androgynous-masc (soft masc): moderate F0 center + high variability.

    Returns True when we should downgrade MASC -> ANDRO_MASC.
    """
    try:
        f0_med = float(data_frame["F0_med"].iloc[0])
        f0_p95 = float(data_frame["F0_p95"].iloc[0])
        f1_med = float(data_frame["F1_med"].iloc[0])

        range_st_5_95 = float(data_frame["range_st_5_95"].iloc[0])
        f0_sd_st = float(data_frame["f0_sd_st"].iloc[0])
        f0_max = float(data_frame["f0_max_hz"].iloc[0])
    except Exception:
        return False

    # Must still be masc-resonant (otherwise this is true-andro or andro-femme territory)
    masc_resonance = np.isfinite(f1_med) and (f1_med <= 440.0)

    # Pitch center in soft-masc band (tune as needed)
    soft_masc_center = np.isfinite(f0_med) and (150.0 <= f0_med <= 175.0)

    # Expressive/variable prosody that reduces "hard masc" perception
    expressive = (
            (np.isfinite(range_st_5_95) and range_st_5_95 >= 16.0) or
            (np.isfinite(f0_sd_st) and f0_sd_st >= 4.9) or
            (np.isfinite(f0_p95) and f0_p95 >= 185.0) or
            (np.isfinite(f0_max) and f0_max >= 430.0)
    )

    # Only apply when the model isn't crazy lopsided
    eps = 1e-9
    ratio = (masc_value + eps) / (femme_value + eps)
    not_extreme_masc = ratio <= 4.5  # tune: 4–10 depending on strictness

    return bool(masc_resonance and soft_masc_center and expressive and not_extreme_masc)


def _femme_requires_support_guardrail(data_frame) -> bool:
    """
    True => downgrade FEMME (insufficient support)
    False => allow FEMME
    """
    try:
        f0_med = float(data_frame["F0_med"].iloc[0])
        f0_mean = float(data_frame["f0_mean_hz"].iloc[0])
        f0_p5_hz = float(data_frame["f0_p5_hz"].iloc[0])
        f0_p5 = float(data_frame["F0_p5"].iloc[0])

        voiced = float(data_frame["voiced_frac"].iloc[0])
        hnr = float(data_frame["hnr_mean_db"].iloc[0])

        f1 = float(data_frame["F1_med"].iloc[0])
        f3f2 = float(data_frame["F3_over_F2"].iloc[0])
        f2f1 = float(data_frame["F2_over_F1"].iloc[0])
    except Exception:
        return False  # fail-open:don't block femme if we can't measure support

    # Strong pitch = sustained + low-tail also high (prevents "tail-only" tricks)
    strong_pitch = (f0_med >= 200 and f0_mean >= 205 and (f0_p5_hz >= 175 or f0_p5 >= 170))

    # General pitch support (weaker)
    pitch_supported = (f0_med >= 175 and f0_mean >= 180)

    # If the pitch is strong, accept lower voicing (your samples often have low voiced_frac)
    voicing_supported = (voiced >= (0.40 if strong_pitch else 0.58))

    # Noise support
    noise_supported = (hnr >= 12.4)  # tune 12-16

    # Only call resonance "dark" when it's quite dark; loosen the cutoff a bit
    resonance_supported = (
            (f1 >= 450.0) or
            (f1 >= 410.0 and f3f2 >= 1.55) or
            (f1 >= 410.0 and f2f1 >= 3.3 and f3f2 >= 1.52)
    )

    # If the pitch is truly strong, allow femme with 2 of: voicing, noise, resonance.
    if strong_pitch:
        supports = 0
        supports += 1 if voicing_supported else 0
        supports += 1 if noise_supported else 0
        supports += 1 if resonance_supported else 0
        return supports < 2

    # Otherwise require at least 3 of 4: pitch + voicing + resonance + noise
    supports = 0
    supports += 1 if pitch_supported else 0
    supports += 1 if voicing_supported else 0
    supports += 1 if resonance_supported else 0
    supports += 1 if noise_supported else 0

    return supports < 3


"""
Logistic regression prediction.
"""


def __predict__():
    """ Takes the users data and predicts the gender perception of the users vocal sample. :return:The predicted value of the users vocal sample.
    """

    user_path = Path.home() / "VocalAnalysisTool" / "user_features.csv"

    blob = joblib.load('gender_model.joblib')

    pipeline = blob["pipeline"]

    feature_names = blob["Feature_names"]

    caps = blob.get("caps", {})

    user_data = pd.read_csv(user_path).iloc[:1].copy()

    user_data["pitch_support_gap_hz"] = user_data["F0_med"] - user_data["F0_p5"]
    eps = 1e-6
    user_data["pitch_support_ratio"] = (user_data["F0_p5"] + eps) / (user_data["F0_med"] + eps)

    for c, (lo, hi) in caps.items():
        if c in user_data.columns:
            user_data[c] = user_data[c].clip(lo, hi)

    user_data = user_data.reindex(columns=feature_names)

    prob = pipeline.predict_proba(user_data)[0]

    masc = prob[0]

    femme = prob[1]

    multiplier = 1.25

    eps = 1e-9

    is_significant = (max(masc, femme) + eps) > multiplier * (min(masc, femme) + eps)

    threshold = 0.05

    if is_significant:

        if femme > masc:

            result = ("FEMME", float(femme)) if user_data["F3_med"].iloc[0] > 2500 else ("MASC", float(masc))
            label, score = result

        else:

            result = "MASC", float(femme)

        unstable_expressive = (
                user_data["F0_med"].iloc[0] >= 160
                and user_data["pitch_support_gap_hz"].iloc[0] >= 60  # big tail collapse
                and user_data["pitch_support_ratio"].iloc[0] <= 0.70  # low tail / center
                and (user_data["range_semitones"].iloc[0] >= 28 or user_data["f0_sd_st"].iloc[0] >= 5)
        )
        print("Pitch support gap: ", user_data["pitch_support_gap_hz"].iloc[0])
        print("Pitch support ratio: ", user_data["pitch_support_ratio"].iloc[0])

        print(result)

        if unstable_expressive:

            print("unstable expressive used")

            diff = femme - masc

            if abs(diff) <= threshold:
                print("Andro threshold")
                result = ("ANDRO", (max((femme - min(femme, masc)), (masc + min(femme, masc)))))
            else:
                result = ("FEMME", float(femme)) if (diff > 0 and user_data["breathiness_index"].iloc[0] < -11) else (
                    "MASC", float(femme))


        if ((user_data["f0_min_hz"].iloc[0] > 290
             or user_data["f0_p5_hz"].iloc[0] > 280)
                and (user_data["F2_med"].iloc[0] < 1650
                     or user_data["hnr_mean_db"].iloc[0] < 16
                     or user_data["f0_sd_st"].iloc[0] < 2.4)
        ):
            result = ("FEMME_FALSETTO", float(0.5))

        if ((femme > masc and (user_data["F1_med"].iloc[0] <= 315)
             and user_data["voiced_frac"].iloc[0] < 0.60
             and user_data["F2_med"].iloc[0] < 1600
             and user_data["F2_over_F1"].iloc[0] < 4.5)
                or ((user_data["F0_med"].iloc[0] >= 240 or
                     user_data["f0_p5_hz"].iloc[0] >= 215)
                    and user_data["range_st_5_95"].iloc[0] < 10.8
                    and user_data["f0_sd_st"].iloc[0] < 4.5
                    and user_data["voiced_frac"].iloc[0] < 0.6
                    and user_data["breathiness_index"].iloc[0] < -18)):
            result = ("ANDRO_FALSETTO", float(0.5))

        if (user_data["f0_min_hz"].iloc[0] <= 80 and user_data["F0_med"].iloc[0] > 140
                and (user_data["f0_max_hz"].iloc[0] > 480
                     and user_data["range_st_5_95"].iloc[0] > 18
                     and (user_data["range_semitones"].iloc[0] > 28
                          or user_data["f0_sd_st"].iloc[0] > 5.5
                          or user_data["hnr_mean_db"].iloc[0] < 12)
                     and user_data["F3_over_F2"].iloc[0] < 1.55
                     and user_data["F2_over_F1"].iloc[0] < 3.2)
        ):
            result = ("MASC_FALSETTO", float(0.5))

    else:
        print("Masc: ", masc, "Femme: ", femme)
        print(f"Threshold: ", ({max(masc, femme) + eps}), ">", ({multiplier * (min(masc, femme) + eps)}))

        diff = femme - masc
        print("Small difference found")

        if abs(diff) <= threshold:
            return "ANDRO", (max((0.5 - min(femme, masc)), (0.5 + min(femme, masc))))

        result = ("ANDRO_FEMME", float(max(femme, masc))) if (diff > 0 and
                                                              (user_data["breathiness_index"].iloc[0] < -11
                                                               or user_data["F1_med"].iloc[0] > 450)) else (
            "ANDRO_MASC", min(femme, masc))

    # Adjust this guardrail once the learning model gains higher confidence.
    # This guardrail prevents false FEMME/MASC due to speaking with a lower/higher falsetto.
    if isinstance(result, tuple) and (len(result) == int(2)):

        label, score = result

        andro_threshold = (min((0.5 - min(femme, masc)), (0.5 + min(femme, masc))))

        if label == "FEMME":
            if _pitch_spike_trap_guardrail(user_data):

                print("Pitch spike used")

                # Penalize for not having proper vocal support
                if user_data["F1_med"].iloc[0] < 400:
                    _penalty = (abs((user_data["F1_med"].iloc[0] - 400)) / 1000)
                    print(_penalty)
                    _score = abs(0.55 - _penalty)
                    print(_score)

                    if _score < 0.45:
                        result = ("ANDRO_MASC", float(_score))
                    elif _score > 0.55:
                        result = ("ANDRO_FEMME", float(_score))
                    else:
                        result = ("ANDRO", float(_score))

                else:
                    result = ("ANDRO", float(min(float(andro_threshold), femme)))

            elif _femme_requires_support_guardrail(user_data):

                print("femme requires support guardrail used")

                # Penalize for not having proper vocal support
                if float(user_data["F0_med"].iloc[0]) < 140:
                    andro_threshold = min(float(andro_threshold), 0.45)

                if 0.45 < andro_threshold < 0.55:
                    result = ("ANDRO", float(andro_threshold))

                elif (3.55 <= user_data["F2_over_F1"].iloc[0] < 4.0 and user_data["F3_over_F2"].iloc[0] > 1.5 and
                      user_data["F0_med"].iloc[0] < 165):
                    _penalty = 0.15
                    _diff = abs(user_data["F2_over_F1"].iloc[0] - 4.0)
                    _score = 0.65 - _diff
                    if user_data["F0_med"].iloc[0] < 145:
                        _score = _score - _penalty
                    result = ("ANDRO_MASC", _score)

                elif user_data["F0_med"].iloc[0] > 165 and user_data["F3_over_F2"].iloc[0] > 1.5:
                    _penalty = abs(round(user_data["F2_over_F1"].iloc[0], 2) - 3.54) / 10
                    print(round(_penalty, 2))
                    _score = 0.65 - round(_penalty, 2)
                    print(round(_score, 2))
                    result = ("ANDRO_FEMME", float(_score))
                else:
                    result = ("MASC", andro_threshold)

        if label == "MASC" and _soft_masc_androgyny_guardrail(user_data, masc_value=masc, femme_value=femme):
            print("soft masc androgyny used")

            result = ("ANDRO_MASC", float(masc))
    print(result)
    return result


def main(file_path=sys.argv[1]):
    _reset_track_state()

    try:
        global FILE_PATH
        FILE_PATH = file_path
        if FILE_PATH:

            sound = parselmouth.Sound(FILE_PATH)

            formant = sound.to_formant_burg(time_step=0.01)
            pitch = sound.to_pitch(time_step=0.01)
            times = np.arange(0, sound.get_total_duration(), 0.01)
            f0_dict = {}
            f1_dict = {}
            f2_dict = {}
            f3_dict = {}
            f4_dict = {}
            # Initializes the formant and pitch

            full_data = []

            for t in times:
                f0 = pitch.get_value_at_time(t)
                f1 = formant.get_value_at_time(1, t)
                f2 = formant.get_value_at_time(2, t)
                f3 = formant.get_value_at_time(3, t)
                f4 = formant.get_value_at_time(4, t)
                if (not math.isnan(f0) and not math.isnan(f1) and not math.isnan(f2) and not math.isnan(f3)
                        and not math.isnan(f4)):
                    f0_dict[float(t)] = f0
                    f1_dict[float(t)] = f1
                    f2_dict[float(t)] = f2
                    f3_dict[float(t)] = f3
                    f4_dict[float(t)] = f4

                    full_data.append([float(round(t, 2)), round(f0, 5), round(f1, 5), round(f2, 5), round(f3, 5),
                                      round(f4, 5)])

            full_data = filter_frequency_synchronized_patch(full_data)

            if not full_data:
                print("No valid frames after filtering; skipping file")
                return None

            row = feature_for_file(sound, full_data, file_id=Path(FILE_PATH).stem)
            _create_csv(row)

            times_, f0_vals_arr, f1_vals_arr, f2_vals_arr, f3_vals_arr, f4_vals_arr = [], [], [], [], [], []

            for i in range(len(full_data)):
                times_.append(full_data[i][0])
                f0_vals_arr.append(full_data[i][1])
                f1_vals_arr.append(full_data[i][2])
                f2_vals_arr.append(full_data[i][3])
                f3_vals_arr.append(full_data[i][4])
                f4_vals_arr.append(full_data[i][5])

            # Gets the average formants
            f0_medians = get_freq_medians(f0_vals_arr)
            f1_medians = get_freq_medians(f1_vals_arr)
            f2_medians = get_freq_medians(f2_vals_arr)
            f3_medians = get_freq_medians(f3_vals_arr)
            f4_medians = get_freq_medians(f4_vals_arr)
            # Crates a list of averages where i = 0 is f0_average and i = 4 is f4_average
            med_formants = [f0_medians, f1_medians, f2_medians, f3_medians, f4_medians]

            # Creates the scatter plot
            png_bytes = plot_formants(times_, f0_vals_arr, f1_vals_arr, f2_vals_arr, f3_vals_arr, f4_vals_arr)

            # Connects to the sqlite db
            connect_table()

            # Inserts the formant data into the sqlite3 database
            gender_label, gender_score = __predict__()

            insert_to_table(times_, f0_vals_arr, f1_vals_arr, med_formants[1], f2_vals_arr, med_formants[2],
                            med_formants[3], med_formants[4], med_formants, png_bytes, gender_label, gender_score)

            return gender_label
    except NameError:
        print("No File Selected")
        print(["No file selected"], file_path)


def __training_test__():
    femme = 0
    masc = 0
    andro_femme = 0
    andro_masc = 0
    path = "D:/Audio Sample for ML/ML_Script/Logistic_Regression/VocalSamples"
    masc_files = "/Masculine Samples/"
    femme_files = "/Feminine Samples/"

    for i in range(1, 87):

        sample_femme = f"female_sample ({i}).wav"
        complete_path = path + femme_files + sample_femme
        result = main(complete_path)

        if result == "FEMME":
            femme += 1

        elif result == "MASC":
            masc += 1

        elif result == "ANDRO_MASC":
            andro_masc += 1

        elif result == "ANDRO_FEMME":
            andro_femme += 1

        print(sample_femme + " completed")

    print("Female samples:")
    print(f"Femme: {femme} Masc: {masc} Andro_Femme: {andro_femme} Andro_masc:{andro_masc}")

    # for j in range(1, 82):
    #     sample_masc = f"male_sample ({j}).wav"
    #     complete_path = path + masc_files + sample_masc
    #     result = main(complete_path)
    #
    #     if result == "FEMME":
    #         femme += 1
    #
    #     elif result == "MASC":
    #         masc += 1
    #
    #     elif result == "ANDRO_MASC":
    #         andro_masc += 1
    #
    #     elif result == "ANDRO_FEMME":
    #         andro_femme += 1
    #     print(sample_masc + " completed")
    #
    # print("Masculine Samples:")
    # print(f"Femme: {femme}\nMasc: {masc}\nAndro_Femme: {andro_femme}\nAndro_masc:{andro_masc}")


if __name__ == "__main__":
    main()
    # __training_test__()
