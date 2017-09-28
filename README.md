# Sparse Hybrid HDP Topic Model

Implementation of a [sparse online Hybrid Variational-Gibbs HDP topic model](http://ecmlpkdd2017.ijs.si/papers/paperID250.pdf). The paper was published at ECML PKDD 2017.

# Compiling and Running the Code # 

- You need Maven
- You need to have [Mallet](https://github.com/mimno/Mallet) version 2.0.9 installed in your maven repository.

To build the project run `mvn package`
You may then run the program with `java -jar target/hybridhdp-1.0-SNAPSHOT.jar linenumber` where linenumber is the line in the `config.txt` file that you want to use. There are some examples for running different methods in `config.txt`.
- Remember to allocate sufficient RAM as needed by adding the option `-Xmx8000m` (for 8GB RAM).

Results will be saved in a folder named project-directory/allresults/dataset-name/...

# Dataset format

The dataset has to be in Mallet's instancelist format. See the [Mallet](https://github.com/mimno/Mallet) site for details. Alternatively you can use a simple text file. A small example is provided in the `data/` folder. The format is: id TAB labels TAB text 
If the dataset does not have labels, simply put a placeholder instead. Examples for both versions are provided in `config.txt`. Control the format using the option `-readInstancelist`

# Algorithms 

The provided algorithms are
- Hybrid Variational-Gibbs HDP
- Hybrid Variational-Gibbs LDA
- HDP (Block Sampler by Chen et al. 2011)
- HDP (Alias Sampler by Li et al. 2014)
- HDP (Our Alias Sampler, 3 different versions)

To choose the sampler you can use the option -whichSampler for HDP. If you want to use the standard sampler for the hybrid method you have to set `-useOriginalSampler true`.
