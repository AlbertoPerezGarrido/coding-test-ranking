package com.idealista.infrastructure.api;

import java.util.*;
import java.util.stream.Collectors;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.idealista.infrastructure.persistence.*;

/** Controller for the ads API.
 * @author Alberto Pérez
 * @version 1.0
 */
@RestController
public class AdsController {

    /** Endpoint for the quality manager
     * @return the list of quality ads
     */
    @GetMapping(value= "/quality-listing", produces = "application/json")
    public ResponseEntity<List<QualityAd>> qualityListing() {
        try {
            HttpHeaders httpHeaders = new HttpHeaders();
            List<QualityAd> adsSorted = createQualityAds().stream()
                    .sorted(Comparator.comparing(QualityAd::getScore).reversed())
                    .collect(Collectors.toList());
            return new ResponseEntity<List<QualityAd>>(adsSorted, httpHeaders, HttpStatus.OK);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    /** Endpoint for the general public
     * @return the list of public ads
     */
    @GetMapping(value= "/public-listing", produces = "application/json")
    public ResponseEntity<List<PublicAd>> publicListing() {
        try {
            HttpHeaders httpHeaders = new HttpHeaders();
            List<PublicAd> adsSorted = getPublicAdsSorted(createPublicAds(), createQualityAds());
            return new ResponseEntity<List<PublicAd>>(adsSorted, httpHeaders, HttpStatus.OK);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    /** Endpoint that calculates the score for each ad
     */
    @GetMapping("/calculate-score")
    public ResponseEntity<Void> calculateScore() {
        try {
            HttpHeaders httpHeaders = new HttpHeaders();
            createQualityAds();
            return new ResponseEntity<Void>(httpHeaders, HttpStatus.OK);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }


    /** Creates a public ad for each ad in the repository
     * @return the list of public ads
     */
    public List<PublicAd> createPublicAds() {
        //We create an instance of the repository to get the ads and pictures
        InMemoryPersistence repository = new InMemoryPersistence();
        List<PublicAd> publicAds = new ArrayList<>();
        List<AdVO> adsVO = repository.getAds();
        List<String> picturesUrls;

        // We create a publicAd for each existing AdVO
        for(AdVO ad : adsVO){
            // Check if it has photos
            if(!ad.getPictures().isEmpty()){
                picturesUrls = repository.getAdPicturesUrls(ad);
            }else{
                picturesUrls = null;
            }

            //We create the public ad without score, that goes into the quality ad
            PublicAd publicAd = new PublicAd();
            publicAd.setId(ad.getId());
            publicAd.setTypology(ad.getTypology());
            publicAd.setDescription(ad.getDescription());
            publicAd.setPictureUrls(picturesUrls);
            publicAd.setHouseSize(ad.getHouseSize());
            publicAd.setGardenSize(ad.getGardenSize());

            // We add each publicAd to the list of public ads
            publicAds.add(publicAd);
        }

        return publicAds;
    }


    /** Creates a quality ad with score for each ad in the repository
     * @return the list of quality ads
     */
    public List<QualityAd> createQualityAds() {

        //We create an instance of the repository to get the ads and pictures
        InMemoryPersistence repository = new InMemoryPersistence();
        List<QualityAd> qualityAds = new ArrayList<>();
        List<AdVO> adsVO = repository.getAds();
        List<String> picturesUrls;
        List<String> picturesQuality;
        int score = 0;
        Date today = new Date();

        // We create a publicAd for each existing AdVO
        for(AdVO ad : adsVO){

            // Check if it has photos
            if(!ad.getPictures().isEmpty()){
                picturesUrls = repository.getAdPicturesUrls(ad);
                picturesQuality = repository.getAdPicturesQuality(ad);
            }else{
                picturesUrls = null;
                picturesQuality = null;
            }

            // We get the score
            score = getAdScore(ad, picturesQuality);

            //We create the equivalent quality ad
            QualityAd qualityAd = new QualityAd();
            qualityAd.setId(ad.getId());
            qualityAd.setTypology(ad.getTypology());
            qualityAd.setDescription(ad.getDescription());
            qualityAd.setPictureUrls(picturesUrls);
            qualityAd.setHouseSize(ad.getHouseSize());
            qualityAd.setGardenSize(ad.getGardenSize());
            qualityAd.setScore(score);
            if(score < 40) {
                qualityAd.setIrrelevantSince(today);
            }else{
                qualityAd.setIrrelevantSince(null);
            }

            // We add each publicAd to the list of public ads
            qualityAds.add(qualityAd);
        }

        return qualityAds;
    }


    /** Returns the score of a determined ad
     * @param ad : The AdVO from the repository
     * @param picturesQuality : the quality of the ad pictures (if there are any)
     * @return the score of the ad
     */
    public int getAdScore(AdVO ad, List<String> picturesQuality){
        int score = 0;

        // We get the score for pictures
        if(ad.getPictures().isEmpty()){
            score -= 10;
        }else{
            if(picturesQuality != null){
                for (String quality : picturesQuality) {
                    if (quality.equals("HD")) {
                        score += 20;
                    } else {
                        score += 10;
                    }
                }
            }
        }

        //We calculate the score for the description text
        String description = ad.getDescription();
        if(!description.equals("")){
            score += 5;
            if(ad.getTypology().equals("FLAT")){
                if(countWords(description) >= 20 && countWords(description) <= 49){
                    score += 10;
                }else if(countWords(description) > 50){
                    score +=30;
                }
            }else if(ad.getTypology().equals("CHALET")){
                if(countWords(description) > 50){
                    score +=20;
                }
            }
            // Search for keywords
            score += checkKeyWords(description);
        }

        //Lastly, we check if the ad is complete
        if(ad.getTypology().equals("GARAGE")){
            if(!ad.getPictures().isEmpty()){
                score += 40;
            }
        }else{
            if(ad.getPictures() != null && !description.equals("") && ad.getHouseSize() != null){
                if(ad.getTypology().equals("FLAT")){
                    score += 40;
                }else if(ad.getTypology().equals("FLAT")){
                    if( ad.getGardenSize() != null){
                        score += 40;
                    }
                }
            }
        }
        if(score < 0){
            score = 0;
        }else if(score > 100){
            score = 100;
        }
        return score;
    }

    /** Checks if the description contains important words
     * @param description : The description for the ad
     * @return the score of the key words
     */
    int checkKeyWords(String description){
        int score = 0;
        List<String> keyWords = Arrays.asList("luminoso", "nuevo", "céntrico", "reformado", "ático");
        String normalized = description.replaceAll("/[.!,]/g", "").toLowerCase();
        for(String keyWord : keyWords){
            if(normalized.contains(keyWord)){
                score += 5;
            }
        }
        return score;
    }

    /** Counts words in a description
     * @param description : The description for the ad
     * @return the number of words counted in the description
     */
    int countWords(String description){
        int words = 0;
        words = description.split(" ").length;
        return words;
    }


    /** Returns the public ads sorted using the quality ads scores
     * @param publicAds : The list of public ads
     * @param qualityAds : The list of quality ads
     * @return the public ads list sorted, but only those with scores over 40
     */
    public List<PublicAd> getPublicAdsSorted(List<PublicAd> publicAds, List<QualityAd> qualityAds){
        List<Integer> orderedIds = new ArrayList<>();
        List<PublicAd> publicAdsSorted = new ArrayList<>();

        // We sort the quality ads first because they have an score
        List<QualityAd> qualityAdsSorted = qualityAds.stream()
                .sorted(Comparator.comparing(QualityAd::getScore).reversed())
                .collect(Collectors.toList());

        // We get the ids ordered, because the id of quality and public ads is the same (only scores >= 40)
        for(QualityAd ad : qualityAdsSorted){
            if(ad.getScore() >= 40){
                orderedIds.add(ad.getId());
            }
        }

        // We sort the public ads
        for(int id : orderedIds){
            for(PublicAd ad : publicAds){
                if(ad.getId().equals(id)){
                    publicAdsSorted.add(ad);
                }
            }
        }

        return publicAdsSorted;
    }
}
